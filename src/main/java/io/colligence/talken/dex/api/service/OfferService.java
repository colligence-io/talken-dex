package io.colligence.talken.dex.api.service;


import io.colligence.talken.common.persistence.enums.DexTaskTypeEnum;
import io.colligence.talken.common.persistence.jooq.tables.records.DexCreateofferTaskRecord;
import io.colligence.talken.common.persistence.jooq.tables.records.DexDeleteofferTaskRecord;
import io.colligence.talken.common.persistence.jooq.tables.records.DexTxResultCreateofferRecord;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.api.dto.CreateOfferResult;
import io.colligence.talken.dex.api.dto.DeleteOfferResult;
import io.colligence.talken.dex.api.dto.DexKeyResult;
import io.colligence.talken.dex.exception.*;
import io.colligence.talken.dex.service.integration.APIResult;
import io.colligence.talken.dex.service.integration.relay.RelayAddContentsResponse;
import io.colligence.talken.dex.service.integration.relay.RelayEncryptedContent;
import io.colligence.talken.dex.service.integration.relay.RelayMsgTypeEnum;
import io.colligence.talken.dex.service.integration.relay.RelayServerService;
import io.colligence.talken.dex.service.integration.stellar.StellarNetworkService;
import io.colligence.talken.dex.util.StellarConverter;
import io.colligence.talken.dex.util.StellarSignVerifier;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;

import java.io.IOException;
import java.security.GeneralSecurityException;
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
	private TokenMetaService maService;

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private RelayServerService relayServerService;

	public CreateOfferResult createOffer(long userId, String sourceAccountId, String sellAssetCode, double sellAssetAmount, String buyAssetCode, double sellAssetPrice, boolean feeByCtx) throws TokenMetaDataNotFoundException, StellarException, APIErrorException, AssetConvertException, InternalServerErrorException {
		DexTaskId dexTaskId = taskIdService.generate_taskId(DexTaskTypeEnum.OFFER_CREATE);

		// create task record
		DexCreateofferTaskRecord taskRecord = new DexCreateofferTaskRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setIspassive(false);
		taskRecord.setSourceaccount(sourceAccountId);
		taskRecord.setSellassetcode(sellAssetCode);
		taskRecord.setSellrequestedamountraw(StellarConverter.doubleToRaw(sellAssetAmount));
		taskRecord.setSellpriceraw(StellarConverter.doubleToRaw(sellAssetPrice));
		taskRecord.setBuyassetcode(buyAssetCode);
		taskRecord.setFeebyctx(feeByCtx);

		dslContext.attach(taskRecord);
		taskRecord.store();

		logger.debug("{} generated. userId = {}", dexTaskId, userId);


		// calculate fee
		FeeCalculationService.Fee fee;
		try {
			fee = feeCalculationService.calculateOfferFee(sellAssetCode, StellarConverter.doubleToRaw(sellAssetAmount), feeByCtx);
		} catch(InternalServerErrorException ex) {
			logger.error("{} failed. : {} {}", dexTaskId, ex.getClass().getSimpleName(), ex.getMessage());

			taskRecord.setErrorposition("calculate fee");
			taskRecord.setErrorcode(ex.getClass().getSimpleName());
			taskRecord.setErrormessage(ex.getMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw ex;
		}

		// build encData for CreateOffer request
		RelayEncryptedContent<BareTxInfo> encData;
		try {
			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(sourceAccountId);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source);

			// get assetType
			Asset buyAssetType = maService.getAssetType(buyAssetCode);

			Transaction.Builder txBuilder = new Transaction
					.Builder(sourceAccount)
					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
					.addMemo(Memo.text(dexTaskId.getId()));

			if(fee.getFeeAmountRaw() > 0) {
				// build fee operation
				txBuilder.addOperation(
						new PaymentOperation
								.Builder(fee.getFeeCollectorAccount(), fee.getFeeAssetType(), StellarConverter.rawToDoubleString(fee.getFeeAmountRaw()))
								.build()
				);
			}

			// build manage offer operation
			txBuilder.addOperation(
					new ManageOfferOperation
							.Builder(fee.getSellAssetType(), buyAssetType, StellarConverter.rawToDoubleString(fee.getSellAmountRaw()), StellarConverter.doubleToString(sellAssetPrice)).setOfferId(0)
							.build()
			);

			// build tx
			BareTxInfo bareTxInfo = BareTxInfo.build(txBuilder.build());

			encData = new RelayEncryptedContent<>(bareTxInfo);

			taskRecord.setSellamountraw(fee.getSellAmountRaw());
			taskRecord.setFeeassetcode(StellarConverter.toAssetCode(fee.getFeeAssetType()));
			taskRecord.setFeeamountraw(fee.getFeeAmountRaw());
			taskRecord.setFeecollectaccount(fee.getFeeCollectorAccount().getAccountId());
			taskRecord.setTxSeq(bareTxInfo.getSequence());
			taskRecord.setTxHash(bareTxInfo.getHash());
			taskRecord.setTxXdr(bareTxInfo.getEnvelopeXdr());
			taskRecord.update();

		} catch(GeneralSecurityException | IOException | RuntimeException ex) {
			logger.error("{} failed. : {} {}", dexTaskId, ex.getClass().getSimpleName(), ex.getMessage());

			taskRecord.setErrorposition("build txData");
			taskRecord.setErrorcode(ex.getClass().getSimpleName());
			taskRecord.setErrormessage(ex.getMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new StellarException(ex);
		}


		// send relay addContents request
		APIResult<RelayAddContentsResponse> relayResult = relayServerService.requestAddContents(RelayMsgTypeEnum.CREATEOFFER, userId, dexTaskId, encData);

		if(!relayResult.isSuccess()) {
			logger.error("{} failed. {}", dexTaskId, relayResult);

			taskRecord.setErrorposition("request relay");
			taskRecord.setErrorcode(String.valueOf(relayResult.getResponseCode()));
			taskRecord.setErrormessage(relayResult.getErrorMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new APIErrorException(relayResult);
		}

		// update task record
		taskRecord.setRlyDexkey(encData.getKey());
		taskRecord.setRlyTransid(relayResult.getData().getTransId());
		taskRecord.setRlyRegdt(relayResult.getData().getRegDt());
		taskRecord.setRlyEnddt(relayResult.getData().getEndDt());
		taskRecord.setSuccessFlag(true);
		taskRecord.update();

		logger.debug("{} complete.", dexTaskId, userId);

		CreateOfferResult result = new CreateOfferResult();
		result.setTaskId(dexTaskId.getId());
		result.setTransId(taskRecord.getRlyTransid());
		result.setSellAssetCode(taskRecord.getSellassetcode());
		result.setSellAmount(StellarConverter.rawToDouble(taskRecord.getSellamountraw()));
		result.setSellPrice(StellarConverter.rawToDouble(taskRecord.getSellpriceraw()));
		result.setBuyAssetCode(taskRecord.getBuyassetcode());
		result.setFeeAssetCode(taskRecord.getFeeassetcode());
		result.setFeeAmount(StellarConverter.rawToDouble(taskRecord.getFeeamountraw()));
		return result;
	}

	public DexKeyResult createOfferDexKey(Long userId, String taskId, String transId, String signature) throws TaskIntegrityCheckFailedException, TaskNotFoundException, SignatureVerificationFailedException {
		DexTaskId dexTaskId = taskIdService.decode_taskId(taskId);
		if(!dexTaskId.getType().equals(DexTaskTypeEnum.OFFER_CREATE))
			throw new TaskNotFoundException(taskId);

		DexCreateofferTaskRecord taskRecord = dslContext.selectFrom(DEX_CREATEOFFER_TASK)
				.where(DEX_CREATEOFFER_TASK.TASKID.eq(taskId))
				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskId));

		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskId);
		if(!taskRecord.getRlyTransid().equals(transId)) throw new TaskIntegrityCheckFailedException(taskId);

		if(!StellarSignVerifier.verifySignBase64(taskRecord.getSourceaccount(), transId, signature))
			throw new SignatureVerificationFailedException(transId, signature);

		DexKeyResult result = new DexKeyResult();
		result.setDexKey(taskRecord.getRlyDexkey());
		return result;
	}

	public DeleteOfferResult deleteOffer(long userId, long offerId, String sourceAccountId, String sellAssetCode, String buyAssetCode, double sellAssetPrice) throws TokenMetaDataNotFoundException, StellarException, APIErrorException {
		DexTaskId dexTaskId = taskIdService.generate_taskId(DexTaskTypeEnum.OFFER_DELETE);

		// create task record
		DexDeleteofferTaskRecord taskRecord = new DexDeleteofferTaskRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setSourceaccount(sourceAccountId);
		taskRecord.setOfferid(offerId);
		taskRecord.setSellassetcode(sellAssetCode);
		taskRecord.setBuyassetcode(buyAssetCode);
		taskRecord.setSellpriceraw(StellarConverter.doubleToRaw(sellAssetPrice));
		dslContext.attach(taskRecord);
		taskRecord.store();

		Optional<DexTxResultCreateofferRecord> opt_dexCreateOfferResultRecord = dslContext.selectFrom(DEX_TX_RESULT_CREATEOFFER)
				.where(DEX_TX_RESULT_CREATEOFFER.OFFERID.eq(offerId))
				.fetchOptional();

		if(opt_dexCreateOfferResultRecord.isPresent()) {
			taskRecord.setCreateofferTaskid(opt_dexCreateOfferResultRecord.get().getTaskid());
		} else {
			// TODO : determine what to do, force proceed? or drop
			logger.warn("Create offer result for {} not found, this may cause unexpected refund result.");
		}

		logger.debug("{} generated. userId = {}", dexTaskId, userId);

		// build encData for DeleteOffer request
		RelayEncryptedContent<BareTxInfo> encData;
		try {
			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(sourceAccountId);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source);

			// get assetType
			Asset sellAssetType = maService.getAssetType(sellAssetCode);
			Asset buyAssetType = maService.getAssetType(buyAssetCode);

			Transaction.Builder txBuilder = new Transaction
					.Builder(sourceAccount)
					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
					.addMemo(Memo.text(dexTaskId.getId()));

			// build manage offer operation
			txBuilder.addOperation(
					new ManageOfferOperation
							.Builder(sellAssetType, buyAssetType, "0", StellarConverter.doubleToString(sellAssetPrice))
							.setOfferId(offerId)
							.build()
			);

			// build tx
			BareTxInfo bareTxInfo = BareTxInfo.build(txBuilder.build());

			encData = new RelayEncryptedContent<>(bareTxInfo);

			taskRecord.setTxSeq(bareTxInfo.getSequence());
			taskRecord.setTxHash(bareTxInfo.getHash());
			taskRecord.setTxXdr(bareTxInfo.getEnvelopeXdr());
			taskRecord.update();

		} catch(GeneralSecurityException | IOException | RuntimeException ex) {
			logger.error("{} failed. : {} {}", dexTaskId, ex.getClass().getSimpleName(), ex.getMessage());

			taskRecord.setErrorposition("build txData");
			taskRecord.setErrorcode(ex.getClass().getSimpleName());
			taskRecord.setErrormessage(ex.getMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new StellarException(ex);
		}


		// send relay addContents request
		APIResult<RelayAddContentsResponse> relayResult = relayServerService.requestAddContents(RelayMsgTypeEnum.DELETEOFFER, userId, dexTaskId, encData);

		if(!relayResult.isSuccess()) {
			logger.error("{} failed. {}", dexTaskId, relayResult);

			taskRecord.setErrorposition("request relay");
			taskRecord.setErrorcode(String.valueOf(relayResult.getResponseCode()));
			taskRecord.setErrormessage(relayResult.getErrorMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new APIErrorException(relayResult);
		}

		// update task record
		taskRecord.setRlyDexkey(encData.getKey());
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
		result.setSellAssetCode(taskRecord.getSellassetcode());
		result.setSellAmount(StellarConverter.rawToDouble(taskRecord.getSellpriceraw()));
		result.setSellPrice(StellarConverter.rawToDouble(taskRecord.getSellpriceraw()));
		return result;
	}

	public DexKeyResult deleteOfferDexKey(Long userId, String taskId, String transId, String signature) throws TaskIntegrityCheckFailedException, TaskNotFoundException, SignatureVerificationFailedException {
		DexTaskId dexTaskId = taskIdService.decode_taskId(taskId);
		if(!dexTaskId.getType().equals(DexTaskTypeEnum.OFFER_DELETE))
			throw new TaskNotFoundException(taskId);

		DexDeleteofferTaskRecord taskRecord = dslContext.selectFrom(DEX_DELETEOFFER_TASK)
				.where(DEX_DELETEOFFER_TASK.TASKID.eq(taskId))
				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskId));

		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskId);
		if(!taskRecord.getRlyTransid().equals(transId)) throw new TaskIntegrityCheckFailedException(taskId);

		if(!StellarSignVerifier.verifySignBase64(taskRecord.getSourceaccount(), transId, signature))
			throw new SignatureVerificationFailedException(transId, signature);

		DexKeyResult result = new DexKeyResult();
		result.setDexKey(taskRecord.getRlyDexkey());
		return result;
	}
}
