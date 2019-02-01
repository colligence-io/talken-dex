package io.colligence.talken.dex.api.dex.offer;


import io.colligence.talken.common.persistence.jooq.tables.records.DexDeleteofferTaskRecord;
import io.colligence.talken.common.persistence.jooq.tables.records.DexMakeofferResultRecord;
import io.colligence.talken.common.persistence.jooq.tables.records.DexMakeofferTaskRecord;
import io.colligence.talken.common.util.JSONWriter;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.api.dex.*;
import io.colligence.talken.dex.api.dex.offer.dto.CreateOfferResult;
import io.colligence.talken.dex.api.dex.offer.dto.DeleteOfferResult;
import io.colligence.talken.dex.api.mas.ma.ManagedAccountService;
import io.colligence.talken.dex.exception.*;
import io.colligence.talken.dex.service.integration.APIResult;
import io.colligence.talken.dex.service.integration.relay.RelayAddContentsRequest;
import io.colligence.talken.dex.service.integration.relay.RelayAddContentsResponse;
import io.colligence.talken.dex.service.integration.relay.RelayServerService;
import io.colligence.talken.dex.service.integration.stellar.StellarNetworkService;
import io.colligence.talken.dex.util.StellarSignVerifier;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Optional;

import static io.colligence.talken.common.persistence.jooq.Tables.*;

@Service
@Scope("singleton")
public class OfferService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(OfferService.class);

	@Autowired
	private DexTaskIdService taskIdService;

	@Autowired
	private FeeCalculationService feeCalculationService;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private ManagedAccountService maService;

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private RelayServerService relayServerService;

	public CreateOfferResult createOffer(long userId, String sourceAccountId, String sellAssetCode, double sellAssetAmount, String buyAssetCode, double sellAssetPrice, boolean feeByCtx) throws AssetTypeNotFoundException, StellarException, APIErrorException {
		DexTaskId dexTaskId = taskIdService.generate_taskId(DexTaskId.Type.OFFER_CREATE);

		// create task record
		DexMakeofferTaskRecord taskRecord = new DexMakeofferTaskRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setIspassive(false);
		taskRecord.setSourceaccount(sourceAccountId);
		taskRecord.setSellassetcode(sellAssetCode);
		taskRecord.setSellrequestedamount(sellAssetAmount);
		taskRecord.setSellprice(sellAssetPrice);
		taskRecord.setBuyassetcode(buyAssetCode);
		taskRecord.setFeebyctx(feeByCtx);

		dslContext.attach(taskRecord);
		taskRecord.store();

		logger.debug("{} generated. userId = {}", dexTaskId, userId);

		// build encData for makeOffer request
		TxEncryptedData encData;
		try {
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

			Transaction.Builder txBuilder = new Transaction
					.Builder(sourceAccount)
					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
					.addMemo(Memo.text(dexTaskId.getId()));

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
			TxInformation txInformation = TxInformation.buildTxInformation(txBuilder.build());

			encData = new TxEncryptedData(txInformation);

			taskRecord.setSellamount(fee.getSellAmount());
			taskRecord.setFeeassetcode(fee.getFeeAssetType().getType());
			taskRecord.setFeeamount(fee.getFeeAmount());
			taskRecord.setFeecollectaccount(fee.getFeeCollectorAccount().getAccountId());
			taskRecord.setTxSeq(txInformation.getSequence());
			taskRecord.setTxHash(txInformation.getHash());
			taskRecord.setTxXdr(txInformation.getEnvelopeXdr());
			taskRecord.setTxKey(encData.getKey());
			taskRecord.update();

		} catch(GeneralSecurityException | IOException ex) {
			logger.error("{} failed. : {} {}", dexTaskId, ex.getClass().getSimpleName(), ex.getMessage());

			taskRecord.setErrorposition("build txData");
			taskRecord.setErrorcode(ex.getClass().getSimpleName());
			taskRecord.setErrormessage(ex.getMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new StellarException(ex);
		}


		// send relay addContents request
		HashMap<String, String> contents = new HashMap<>();
		contents.put("taskId", dexTaskId.getId());
		contents.put("data", encData.getEncrypted());
		RelayAddContentsRequest rlyRequest = new RelayAddContentsRequest();
		rlyRequest.setMsgType("1005");
		rlyRequest.setUserId(Long.toString(userId));
		rlyRequest.setMsgContents(JSONWriter.toJsonStringSafe(contents));
		rlyRequest.setPushTitle("");
		rlyRequest.setPushBody("");

		APIResult<RelayAddContentsResponse> relayResult = relayServerService.requestAddContents(rlyRequest);

		if(!relayResult.isSuccess()) {
			logger.error("{} failed. {}", dexTaskId, relayResult);

			taskRecord.setErrorposition("request relay");
			taskRecord.setErrorcode(relayResult.getResponseCode());
			taskRecord.setErrormessage(relayResult.getErrorMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new APIErrorException(relayResult);
		}

		// update task record
		taskRecord.setRlyTransid(relayResult.getData().getTransId());
		taskRecord.setRlyRegdt(relayResult.getData().getRegDt());
		taskRecord.setRlyEnddt(relayResult.getData().getEndDt());
		taskRecord.setSuccessFlag(true);
		taskRecord.update();

		logger.debug("{} complete.", dexTaskId, userId);

		CreateOfferResult result = new CreateOfferResult();
		result.setTaskId(dexTaskId.getId());
		result.setTransId(taskRecord.getRlyTransid());
		result.setSellAssetType(taskRecord.getSellassetcode());
		result.setSellAmount(taskRecord.getSellamount());
		result.setSellPrice(taskRecord.getSellprice());
		result.setBuyAssetType(taskRecord.getBuyassetcode());
		result.setFeeAssetType(taskRecord.getFeeassetcode());
		result.setFeeAmount(taskRecord.getFeeamount());
		return result;
	}

	public DexKeyResult createOfferDexKey(Long userId, String taskId, String transId, String signature) throws TaskIntegrityCheckFailedException, TaskNotFoundException, SignatureVerificationFailedException {
		DexTaskId dexTaskId = taskIdService.decode_taskId(taskId);
		if(!dexTaskId.getType().equals(DexTaskId.Type.OFFER_CREATE))
			throw new TaskNotFoundException(taskId);

		DexMakeofferTaskRecord taskRecord = dslContext.selectFrom(DEX_MAKEOFFER_TASK)
				.where(DEX_ANCHOR_TASK.TASKID.eq(taskId))
				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskId));

		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskId);
		if(!taskRecord.getRlyTransid().equals(transId)) throw new TaskIntegrityCheckFailedException(taskId);

		if(!StellarSignVerifier.verifySignBase64(taskRecord.getSourceaccount(), transId, signature))
			throw new SignatureVerificationFailedException(transId, signature);

		DexKeyResult result = new DexKeyResult();
		result.setDexKey(taskRecord.getTxKey());
		return result;
	}

	public DeleteOfferResult deleteOffer(long userId, long offerId, String sourceAccountId, String sellAssetCode, String buyAssetCode, double sellAssetPrice) throws AssetTypeNotFoundException, StellarException, APIErrorException {
		DexTaskId dexTaskId = taskIdService.generate_taskId(DexTaskId.Type.OFFER_DELETE);

		// create task record
		DexDeleteofferTaskRecord taskRecord = new DexDeleteofferTaskRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setSourceaccount(sourceAccountId);
		taskRecord.setOfferid(offerId);
		taskRecord.setSellassetcode(sellAssetCode);
		taskRecord.setBuyassetcode(buyAssetCode);
		taskRecord.setSellprice(sellAssetPrice);
		dslContext.attach(taskRecord);
		taskRecord.store();

		Optional<DexMakeofferResultRecord> opt_dexMakeofferResultRecord = dslContext.selectFrom(DEX_MAKEOFFER_RESULT)
				.where(DEX_MAKEOFFER_RESULT.OFFERID.eq(offerId))
				.fetchOptional();

		if(opt_dexMakeofferResultRecord.isPresent()) {
			taskRecord.setMakeoffertaskid(opt_dexMakeofferResultRecord.get().getTaskid());
		} else {
			// TODO : determine what to do, force proceed? or drop
			logger.warn("Make offer result for {} not found, this may cause unexpected refund result.");
		}

		logger.debug("{} generated. userId = {}", dexTaskId, userId);

		// build encData for makeOffer request
		TxEncryptedData encData;
		try {
			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(sourceAccountId);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source);

			// get assetType
			AssetTypeCreditAlphaNum4 sellAssetType = maService.getAssetType(sellAssetCode);
			AssetTypeCreditAlphaNum4 buyAssetType = maService.getAssetType(buyAssetCode);

			Transaction.Builder txBuilder = new Transaction
					.Builder(sourceAccount)
					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
					.addMemo(Memo.text(dexTaskId.getId()));

			// build manage offer operation
			txBuilder.addOperation(
					new ManageOfferOperation
							.Builder(sellAssetType, buyAssetType, Double.toString(0), Double.toString(sellAssetPrice))
							.setOfferId(offerId)
							.build()
			);

			// build tx
			TxInformation txInformation = TxInformation.buildTxInformation(txBuilder.build());

			encData = new TxEncryptedData(txInformation);

			taskRecord.setTxSeq(txInformation.getSequence());
			taskRecord.setTxHash(txInformation.getHash());
			taskRecord.setTxXdr(txInformation.getEnvelopeXdr());
			taskRecord.setTxKey(encData.getKey());
			taskRecord.update();

		} catch(GeneralSecurityException | IOException ex) {
			logger.error("{} failed. : {} {}", dexTaskId, ex.getClass().getSimpleName(), ex.getMessage());

			taskRecord.setErrorposition("build txData");
			taskRecord.setErrorcode(ex.getClass().getSimpleName());
			taskRecord.setErrormessage(ex.getMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new StellarException(ex);
		}


		// send relay addContents request
		HashMap<String, String> contents = new HashMap<>();
		contents.put("taskId", dexTaskId.getId());
		contents.put("data", encData.getEncrypted());
		RelayAddContentsRequest rlyRequest = new RelayAddContentsRequest();
		rlyRequest.setMsgType("1006");
		rlyRequest.setUserId(Long.toString(userId));
		rlyRequest.setMsgContents(JSONWriter.toJsonStringSafe(contents));
		rlyRequest.setPushTitle("");
		rlyRequest.setPushBody("");

		APIResult<RelayAddContentsResponse> relayResult = relayServerService.requestAddContents(rlyRequest);

		if(!relayResult.isSuccess()) {
			logger.error("{} failed. {}", dexTaskId, relayResult);

			taskRecord.setErrorposition("request relay");
			taskRecord.setErrorcode(relayResult.getResponseCode());
			taskRecord.setErrormessage(relayResult.getErrorMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new APIErrorException(relayResult);
		}

		// update task record
		taskRecord.setRlyTransid(relayResult.getData().getTransId());
		taskRecord.setRlyRegdt(relayResult.getData().getRegDt());
		taskRecord.setRlyEnddt(relayResult.getData().getEndDt());
		taskRecord.setSuccessFlag(true);
		taskRecord.update();

		logger.debug("{} complete.", dexTaskId, userId);

		DeleteOfferResult result = new DeleteOfferResult();
		result.setTaskId(dexTaskId.getId());
		result.setTransId(taskRecord.getRlyTransid());
		result.setOfferId(taskRecord.getOfferid());
		result.setSellAssetType(taskRecord.getSellassetcode());
		result.setSellAmount(taskRecord.getSellprice());
		result.setSellPrice(taskRecord.getSellprice());
		return result;
	}

	public DexKeyResult deleteOfferDexKey(Long userId, String taskId, String transId, String signature) throws TaskIntegrityCheckFailedException, TaskNotFoundException, SignatureVerificationFailedException {
		DexTaskId dexTaskId = taskIdService.decode_taskId(taskId);
		if(!dexTaskId.getType().equals(DexTaskId.Type.OFFER_DELETE))
			throw new TaskNotFoundException(taskId);

		DexDeleteofferTaskRecord taskRecord = dslContext.selectFrom(DEX_DELETEOFFER_TASK)
				.where(DEX_ANCHOR_TASK.TASKID.eq(taskId))
				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskId));

		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskId);
		if(!taskRecord.getRlyTransid().equals(transId)) throw new TaskIntegrityCheckFailedException(taskId);

		if(!StellarSignVerifier.verifySignBase64(taskRecord.getSourceaccount(), transId, signature))
			throw new SignatureVerificationFailedException(transId, signature);

		DexKeyResult result = new DexKeyResult();
		result.setDexKey(taskRecord.getTxKey());
		return result;
	}
}
