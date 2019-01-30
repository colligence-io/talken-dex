package io.colligence.talken.dex.api.dex.offer;

import io.colligence.talken.common.persistence.jooq.tables.records.DexMakeofferTakelistRecord;
import io.colligence.talken.common.persistence.jooq.tables.records.DexMakeofferTaskRecord;
import io.colligence.talken.common.util.JSONWriter;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.api.DexTaskId;
import io.colligence.talken.dex.api.dex.FeeCalculationService;
import io.colligence.talken.dex.api.dex.TxInformation;
import io.colligence.talken.dex.api.dex.TxSubmitResult;
import io.colligence.talken.dex.api.dex.offer.dto.*;
import io.colligence.talken.dex.api.mas.ma.ManagedAccountService;
import io.colligence.talken.dex.exception.*;
import io.colligence.talken.dex.service.integration.APIResult;
import io.colligence.talken.dex.service.integration.stellar.StellarNetworkService;
import io.colligence.talken.dex.util.TransactionBlockExecutor;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Asset;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.xdr.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.colligence.talken.common.persistence.jooq.Tables.DEX_ANCHOR_TASK;
import static io.colligence.talken.common.persistence.jooq.Tables.DEX_MAKEOFFER_TASK;

@Service
@Scope("singleton")
public class OfferService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(OfferService.class);

	@Autowired
	private FeeCalculationService feeCalculationService;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private ManagedAccountService maService;

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private DataSourceTransactionManager txMgr;

	public CreateOfferResult buildCreateOfferTx(long userId, String sourceAccountId, String sellAssetCode, double sellAssetAmount, String buyAssetCode, double sellAssetPrice, boolean feeByCtx) throws AssetTypeNotFoundException, StellarException {

		DexTaskId dexTaskId = DexTaskId.generate(DexTaskId.Type.OFFER_CREATE);

		// create task record
		DexMakeofferTaskRecord taskRecord = new DexMakeofferTaskRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setIspassive(false);
		taskRecord.setSourceaccount(sourceAccountId);
		taskRecord.setSellassettype(sellAssetCode);
		taskRecord.setSellrequestedamount(sellAssetAmount);
		taskRecord.setSellprice(sellAssetPrice);
		taskRecord.setBuyassettype(buyAssetCode);

		dslContext.attach(taskRecord);
		taskRecord.store();

		logger.debug("{} generated. userId = {}", dexTaskId, userId);

		try {
			String taskID = DexTaskId.generate(DexTaskId.Type.OFFER_CREATE).toString();
			// TODO : unique check, insert into db

			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(sourceAccountId);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source);

			// calculate fee
			FeeCalculationService.Fee fee = feeCalculationService.calculateOfferFee(sellAssetCode, sellAssetAmount, feeByCtx);

			// get assetType
			AssetTypeCreditAlphaNum4 buyAssetType = maService.getAssetType(buyAssetCode);

			Transaction.Builder txBuilder = new Transaction.Builder(sourceAccount).setTimeout(Transaction.Builder.TIMEOUT_INFINITE);

			if(fee.getFeeAmount() > 0) {
				// build fee operation
				txBuilder.addOperation(
						new PaymentOperation
								.Builder(fee.getFeeCollectorAccount(), fee.getFeeAssetType(), Double.toString(fee.getFeeAmount()))
								.build()
				);
			}

			// build manage offer operation
			txBuilder.addOperation(
					new ManageOfferOperation
							.Builder(fee.getSellAssetType(), buyAssetType, Double.toString(fee.getSellAmount()), Double.toString(sellAssetPrice)).setOfferId(0)
							.build()
			);

			// build tx
			Transaction tx = txBuilder.build();
			TxInformation txInformation = TxInformation.buildTxInformation(tx);

			taskRecord.setSellamount(fee.getSellAmount());
			taskRecord.setFeeassettype(fee.getFeeAssetType().getType());
			taskRecord.setFeeamount(fee.getFeeAmount());
			taskRecord.setFeecollectaccount(fee.getFeeCollectorAccount().getAccountId());
			taskRecord.setTxhash(txInformation.getHash());
			taskRecord.setTxxdr(txInformation.getEnvelopeXdr());
			taskRecord.update();

			logger.debug("{} updated. txHash = {}", dexTaskId, txInformation.getHash());

			CreateOfferResult result = new CreateOfferResult();
			result.setTaskId(taskID);
			result.setTxInformation(txInformation);
			result.setFeeAmount(fee.getFeeAmount());
			result.setFeeAssetType(fee.getFeeAssetType().getType());

			return result;
		} catch(IOException ioex) {
			throw new StellarException(ioex);
		}
	}

	public CreateOfferSubmitResult submitCreateOfferTx(long userId, String taskId, String txHash, String txXdr) throws TransactionHashNotMatchException, APIErrorException, TaskIntegrityCheckFailedException, TaskNotFoundException, StellarException, TransactionResultProcessingException {
		DexTaskId dexTaskId = DexTaskId.fromId(taskId);
		DexMakeofferTaskRecord taskRecord = dslContext.selectFrom(DEX_MAKEOFFER_TASK)
				.where(DEX_ANCHOR_TASK.TASKID.eq(taskId))
				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskId));

		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskId);

		taskRecord.setTxxdrsigned(txXdr);

		APIResult<TxSubmitResult> stellarTxResult = stellarNetworkService.submitTx(taskId, txHash, txXdr);

		taskRecord.setTxSuccessFlag(stellarTxResult.isSuccess());
		taskRecord.setTxResult(JSONWriter.toJsonStringSafe(stellarTxResult.getData()));

		if(!stellarTxResult.isSuccess()) {

			logger.debug("{} submit failed. : {}", dexTaskId, stellarTxResult.getErrorMessage());
			taskRecord.setTxErrorcode(stellarTxResult.getErrorCode());
			taskRecord.setTxErrormessage(stellarTxResult.getErrorMessage());
			taskRecord.update();

			throw new APIErrorException(stellarTxResult);
		}

		taskRecord.update();

		CreateOfferSubmitResult result = new CreateOfferSubmitResult();
		result.setTxSubmitResult(stellarTxResult.getData());

		// decode txResult;
		TransactionResult txResult;
		try {
			txResult = stellarTxResult.getData().decode();
		} catch(IOException ex) {

			// update task record
			logger.error("{} txXdr parsing failed. : {}", dexTaskId, ex.getMessage());

			taskRecord.setErrorcode("IOException");
			taskRecord.setErrormessage("XDR Parsing failed : " + ex.getMessage());
			taskRecord.update();

			throw new StellarException(ex);
		}

		// extract feeResult and offerResult
		PaymentResult feeResult = null;
		ManageOfferResult offerResult = null;

		for(OperationResult operationResult : txResult.getResult().getResults()) {
			if(operationResult.getTr().getDiscriminant() == OperationType.PAYMENT) {
				feeResult = operationResult.getTr().getPaymentResult();
			}
			if(operationResult.getTr().getDiscriminant() == OperationType.MANAGE_OFFER) {
				offerResult = operationResult.getTr().getManageOfferResult();
			}
		}

		double makeAmount = 0;
		double takeAmount = 0;
		double feeAmount = taskRecord.getFeeamount();

		if(feeAmount != 0 && feeResult == null) {
			// TODO : WTF?

		}

		if(offerResult == null || offerResult.getSuccess() == null) {
			// NOTE : WTF?

			logger.error("{} offer result {} processing error : no offer success result", dexTaskId, txHash);

			taskRecord.setErrorcode("NoOfferSuccessResult");
			taskRecord.update();

			throw new TransactionResultProcessingException(dexTaskId.toString(), "NoOfferSuccessResult");
		}

		List<DexMakeofferTakelistRecord> takeList = new ArrayList<>();
		if(offerResult.getSuccess().getOffersClaimed() != null) {
			for(ClaimOfferAtom claimed : offerResult.getSuccess().getOffersClaimed()) {
				DexMakeofferTakelistRecord takeRecord = new DexMakeofferTakelistRecord();
				takeRecord.setTaskid(taskId);
				takeRecord.setSelleraccount(KeyPair.fromXdrPublicKey(claimed.getSellerID().getAccountID()).getAccountId());
				takeRecord.setOfferid(claimed.getOfferID().getUint64());
				takeRecord.setSoldassettype(Asset.fromXdr(claimed.getAssetSold()).getType());
				double soldAmount = (double) claimed.getAmountSold().getInt64() / 10000000;
				takeAmount += soldAmount;
				takeRecord.setSoldamount(soldAmount);
				takeRecord.setBoughtassettype(Asset.fromXdr(claimed.getAssetBought()).getType());
				takeRecord.setBoughtamount((double) claimed.getAmountBought().getInt64() / 10000000);
				takeList.add(takeRecord);
			}
		}

		OfferEntry made = offerResult.getSuccess().getOffer().getOffer();
		if(made != null) {
			result.setOfferId(made.getOfferID().getUint64());
			taskRecord.setMadeofferid(made.getOfferID().getUint64());
			makeAmount = (double) made.getAmount().getInt64() / 10000000;
			taskRecord.setMadeofferamount(makeAmount);
		}

		double refundAmount = feeAmount * makeAmount / (makeAmount + takeAmount);

		result.setMakeAmount(makeAmount);
		result.setTakeAmount(takeAmount);
		result.setRefundAmount(refundAmount);

		taskRecord.setRefundfeeamount(refundAmount);

		DexTaskId refundTaskId = feeCalculationService.createOfferFeeRefundTask(taskRecord.getFeecollectaccount(), taskRecord.getSourceaccount(), refundAmount);

		taskRecord.setRefundtaskid(refundTaskId.getId());

		try {
			TransactionBlockExecutor.of(txMgr).transactional(() -> {
				for(DexMakeofferTakelistRecord takelistRecord : takeList) {
					dslContext.attach(takelistRecord);
					takelistRecord.store();
				}
				taskRecord.update();
			});
		} catch(Exception e) {

			taskRecord.setErrorcode(e.getClass().getName());
			taskRecord.setErrormessage(e.getMessage());
			taskRecord.update();

			throw new TransactionResultProcessingException(e, dexTaskId.toString());
		}

		return result;
	}

	public CreatePassiveOfferResult buildCreatePassiveOfferTx(long userId, String sourceAccountID, String sellAssetCode, double sellAssetAmount, String buyAssetCode, double sellAssetPrice, boolean feeByCtx) throws AssetTypeNotFoundException, StellarException {
		try {
			String taskID = DexTaskId.generate(DexTaskId.Type.OFFER_CREATEPASSIVE).toString();
			// TODO : unique check, insert into db

			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(sourceAccountID);
//			KeyPair feeDestination = maService.getOfferFeeHolderAccount(sellAssetCode);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source);

			// calculate fee
			FeeCalculationService.Fee fee = feeCalculationService.calculateOfferFee(sellAssetCode, sellAssetAmount, feeByCtx);

			// get assetType
			AssetTypeCreditAlphaNum4 buyAssetType = maService.getAssetType(buyAssetCode);

			Transaction.Builder txBuilder = new Transaction.Builder(sourceAccount).setTimeout(Transaction.Builder.TIMEOUT_INFINITE);

			if(fee.getFeeAmount() > 0) {
				// build fee operation
				txBuilder.addOperation(
						new PaymentOperation
								.Builder(fee.getFeeCollectorAccount(), fee.getFeeAssetType(), Double.toString(fee.getFeeAmount()))
								.build()
				);
			}

			// build manage offer operation
			txBuilder.addOperation(
					new CreatePassiveOfferOperation
							.Builder(fee.getSellAssetType(), buyAssetType, Double.toString(fee.getSellAmount()), Double.toString(sellAssetPrice))
							.build()
			);

			// build tx
			Transaction tx = txBuilder.build();

			// TODO : insert into taskDB

			return new CreatePassiveOfferResult(taskID, TxInformation.buildTxInformation(tx));
		} catch(IOException ioex) {
			throw new StellarException(ioex);
		}
	}

	public CreatePassiveOfferSubmitResult submitCreatePassiveOfferTx(long userId, String taskID, String txHash, String txXdr) throws TransactionHashNotMatchException, APIErrorException {
		// TODO : update taskDB

		// build refund tx and send it to MAS

		APIResult<TxSubmitResult> stellarTxResult = stellarNetworkService.submitTx(taskID, txHash, txXdr);
		if(!stellarTxResult.isSuccess()) {
			throw new APIErrorException(stellarTxResult);
		}

		return new CreatePassiveOfferSubmitResult(stellarTxResult.getData());
	}

	public DeleteOfferResult buildDeleteOfferTx(long userId, long offerId, String sourceAccountID, String sellAssetCode, String buyAssetCode, double sellAssetPrice) throws AssetTypeNotFoundException, StellarException {
		try {
			String taskID = DexTaskId.generate(DexTaskId.Type.OFFER_DELETE).toString();
			// TODO : unique check, insert into db

			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(sourceAccountID);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source);

			// get assetType
			AssetTypeCreditAlphaNum4 sellAssetType = maService.getAssetType(sellAssetCode);
			AssetTypeCreditAlphaNum4 buyAssetType = maService.getAssetType(buyAssetCode);


			// build manage offer operation
			ManageOfferOperation deleteOfferOperation = new ManageOfferOperation
					.Builder(sellAssetType, buyAssetType, Double.toString(0), Double.toString(sellAssetPrice))
					.setOfferId(offerId)
					.build();

			// build tx
			Transaction tx = new Transaction
					.Builder(sourceAccount)
					.addOperation(deleteOfferOperation)
					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
					.build();

			// TODO : insert task into db

			return new DeleteOfferResult(taskID, TxInformation.buildTxInformation(tx));
		} catch(IOException ioex) {
			throw new StellarException(ioex);
		}
	}

	public DeleteOfferSubmitResult submitDeleteOfferTx(long userId, String taskID, String txHash, String txXdr) throws TransactionHashNotMatchException, APIErrorException {
		// TODO : update taskDB

		// build refund tx and send it to MAS

		APIResult<TxSubmitResult> stellarTxResult = stellarNetworkService.submitTx(taskID, txHash, txXdr);
		if(!stellarTxResult.isSuccess()) {
			throw new APIErrorException(stellarTxResult);
		}

		return new DeleteOfferSubmitResult(stellarTxResult.getData());
	}
}
