package io.talken.dex.api.service;


import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskCreateofferRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskDeleteofferRecord;
import io.talken.common.persistence.jooq.tables.records.DexTxmonCreateofferRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.dto.CreateOfferResult;
import io.talken.dex.api.controller.dto.DeleteOfferResult;
import io.talken.dex.api.controller.dto.DexKeyResult;
import io.talken.dex.api.service.integration.relay.RelayAddContentsResponse;
import io.talken.dex.api.service.integration.relay.RelayEncryptedContent;
import io.talken.dex.api.service.integration.relay.RelayMsgTypeEnum;
import io.talken.dex.api.service.integration.relay.RelayServerService;
import io.talken.dex.shared.BareTxInfo;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.StellarConverter;
import io.talken.dex.shared.StellarSignVerifier;
import io.talken.dex.shared.exception.*;
import io.talken.dex.shared.service.StellarNetworkService;
import io.talken.dex.shared.service.integration.APIResult;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.*;

@Service
@Scope("singleton")
public class OfferService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(OfferService.class);

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

	public CreateOfferResult createOffer(long userId, String sourceAccountId, String sellAssetCode, double sellAssetAmount, String buyAssetCode, double sellAssetPrice, boolean feeByCtx) throws TokenMetaDataNotFoundException, StellarException, APIErrorException, AssetConvertException, TokenMetaLoadException {
		DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.OFFER_CREATE);

		// create task record
		DexTaskCreateofferRecord taskRecord = new DexTaskCreateofferRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setIspassive(false);
		taskRecord.setSourceaccount(sourceAccountId);
		taskRecord.setSellassetcode(sellAssetCode);
		taskRecord.setSellrequestedamountraw(StellarConverter.actualToRaw(sellAssetAmount));
		taskRecord.setSellpriceraw(StellarConverter.actualToRaw(sellAssetPrice));
		taskRecord.setBuyassetcode(buyAssetCode);
		taskRecord.setFeebyctx(feeByCtx);

		dslContext.attach(taskRecord);
		taskRecord.store();

		logger.info("{} generated. userId = {}", dexTaskId, userId);


		// calculate fee
		FeeCalculationService.Fee fee;
		try {
			fee = feeCalculationService.calculateOfferFee(sellAssetCode, StellarConverter.actualToRaw(sellAssetAmount), feeByCtx);
		} catch(TokenMetaLoadException ex) {
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

			Transaction.Builder txBuilder = stellarNetworkService.getTransactionBuilderFor(sourceAccount)
					.addMemo(Memo.text(dexTaskId.getId()));

			if(fee.getFeeAmountRaw() > 0) {
				// build fee operation
				txBuilder.addOperation(
						new PaymentOperation
								.Builder(fee.getFeeCollectorAccount(), fee.getFeeAssetType(), StellarConverter.rawToActualString(fee.getFeeAmountRaw()))
								.build()
				);
			}

			// build manage offer operation
			txBuilder.addOperation(
					new ManageOfferOperation
							.Builder(fee.getSellAssetType(), buyAssetType, StellarConverter.rawToActualString(fee.getSellAmountRaw()), StellarConverter.actualToString(sellAssetPrice)).setOfferId(0)
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

		// set trans description
		encData.addDescription("tradeWalletAddress", taskRecord.getSourceaccount());
		encData.addDescription("sellAssetCode", taskRecord.getSellassetcode());
		encData.addDescription("sellPrice", StellarConverter.rawToActualString(taskRecord.getSellpriceraw()));
		encData.addDescription("sellAmount", StellarConverter.rawToActualString(taskRecord.getSellamountraw()));
		encData.addDescription("buyAssetCode", taskRecord.getBuyassetcode());
		encData.addDescription("feeAssetCode", taskRecord.getFeeassetcode());
		encData.addDescription("feeAmount", StellarConverter.rawToActualString(taskRecord.getFeeamountraw()));

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
		result.setSellAmount(StellarConverter.rawToActual(taskRecord.getSellamountraw()).doubleValue());
		result.setSellPrice(StellarConverter.rawToActual(taskRecord.getSellpriceraw()).doubleValue());
		result.setBuyAssetCode(taskRecord.getBuyassetcode());
		result.setFeeAssetCode(taskRecord.getFeeassetcode());
		result.setFeeAmount(StellarConverter.rawToActual(taskRecord.getFeeamountraw()).doubleValue());
		return result;
	}

	public DexKeyResult createOfferDexKey(Long userId, String taskId, String transId, String signature) throws TaskIntegrityCheckFailedException, TaskNotFoundException, SignatureVerificationFailedException {
		DexTaskId dexTaskId = DexTaskId.decode_taskId(taskId);
		if(!dexTaskId.getType().equals(DexTaskTypeEnum.OFFER_CREATE))
			throw new TaskNotFoundException(taskId);

		DexTaskCreateofferRecord taskRecord = dslContext.selectFrom(DEX_TASK_CREATEOFFER)
				.where(DEX_TASK_CREATEOFFER.TASKID.eq(taskId))
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
		DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.OFFER_DELETE);

		// create task record
		DexTaskDeleteofferRecord taskRecord = new DexTaskDeleteofferRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setSourceaccount(sourceAccountId);
		taskRecord.setOfferid(offerId);
		taskRecord.setSellassetcode(sellAssetCode);
		taskRecord.setBuyassetcode(buyAssetCode);
		taskRecord.setSellpriceraw(StellarConverter.actualToRaw(sellAssetPrice));
		dslContext.attach(taskRecord);
		taskRecord.store();

		Optional<DexTxmonCreateofferRecord> opt_dexCreateOfferResultRecord = dslContext.selectFrom(DEX_TXMON_CREATEOFFER)
				.where(DEX_TXMON_CREATEOFFER.OFFERID.eq(offerId))
				.fetchOptional();

		if(opt_dexCreateOfferResultRecord.isPresent()) {
			taskRecord.setCreateofferTaskid(opt_dexCreateOfferResultRecord.get().getTaskidCrof());
		} else {
			// TODO : determine what to do, force proceed? or drop
			logger.warn("Create offer result for {} not found, this may cause unexpected refund result.");
		}

		logger.info("{} generated. userId = {}", dexTaskId, userId);

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

			Transaction.Builder txBuilder = stellarNetworkService.getTransactionBuilderFor(sourceAccount)
					.addMemo(Memo.text(dexTaskId.getId()));

			// build manage offer operation
			txBuilder.addOperation(
					new ManageOfferOperation
							.Builder(sellAssetType, buyAssetType, "0", StellarConverter.actualToString(sellAssetPrice))
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

		// set trans description
		encData.addDescription("tradeWalletAddress", taskRecord.getSourceaccount());
		encData.addDescription("offerId", Long.toString(taskRecord.getOfferid()));
		encData.addDescription("sellAssetCode", taskRecord.getSellassetcode());
		encData.addDescription("buyAssetCode", StellarConverter.rawToActualString(taskRecord.getSellpriceraw()));
		encData.addDescription("sellPrice", StellarConverter.rawToActualString(taskRecord.getSellpriceraw()));

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
		result.setSellAmount(StellarConverter.rawToActual(taskRecord.getSellpriceraw()).doubleValue());
		result.setSellPrice(StellarConverter.rawToActual(taskRecord.getSellpriceraw()).doubleValue());
		return result;
	}

	public DexKeyResult deleteOfferDexKey(Long userId, String taskId, String transId, String signature) throws TaskIntegrityCheckFailedException, TaskNotFoundException, SignatureVerificationFailedException {
		DexTaskId dexTaskId = DexTaskId.decode_taskId(taskId);
		if(!dexTaskId.getType().equals(DexTaskTypeEnum.OFFER_DELETE))
			throw new TaskNotFoundException(taskId);

		DexTaskDeleteofferRecord taskRecord = dslContext.selectFrom(DEX_TASK_DELETEOFFER)
				.where(DEX_TASK_DELETEOFFER.TASKID.eq(taskId))
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
