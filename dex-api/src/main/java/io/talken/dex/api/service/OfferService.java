package io.talken.dex.api.service;


import io.talken.common.exception.common.IntegrationException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskCreateofferRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskDeleteofferRecord;
import io.talken.common.persistence.jooq.tables.records.DexTxmonCreateofferRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.IntegrationResult;
import io.talken.dex.api.controller.dto.CalculateFeeResult;
import io.talken.dex.api.controller.dto.CreateOfferResult;
import io.talken.dex.api.controller.dto.DeleteOfferResult;
import io.talken.dex.api.controller.dto.DexKeyResult;
import io.talken.dex.api.service.integration.relay.RelayAddContentsResponse;
import io.talken.dex.api.service.integration.relay.RelayEncryptedContent;
import io.talken.dex.api.service.integration.relay.RelayMsgTypeEnum;
import io.talken.dex.api.service.integration.relay.RelayServerService;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.exception.*;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import io.talken.dex.shared.service.blockchain.stellar.StellarRawTxInfo;
import io.talken.dex.shared.service.blockchain.stellar.StellarSignVerifier;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.*;


// FIXME : disabled before applying stellar-sdk 0.9.0
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

	public CreateOfferResult createOffer(long userId, String sourceAccountId, DexTaskTypeEnum taskType, String sellAssetCode, String buyAssetCode, BigDecimal amount, BigDecimal price, boolean feeByTalk) throws TokenMetaNotFoundException, StellarException, AssetConvertException, EffectiveAmountIsNegativeException, IntegrationException {
		// passive sell is not supported yet
		if(!(taskType != null && (taskType.equals(DexTaskTypeEnum.OFFER_CREATE_BUY) || taskType.equals(DexTaskTypeEnum.OFFER_CREATE_SELL)))) {
			throw new IllegalArgumentException("TaskType " + taskType + " is not supported by createOffer");
		}

		DexTaskId dexTaskId = DexTaskId.generate_taskId(taskType);

		// create task record
		DexTaskCreateofferRecord taskRecord = new DexTaskCreateofferRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);
		taskRecord.setTasktype(taskType);
		taskRecord.setSourceaccount(sourceAccountId);
		taskRecord.setSellassetcode(sellAssetCode);
		taskRecord.setBuyassetcode(buyAssetCode);
		taskRecord.setRequestamountraw(StellarConverter.actualToRaw(amount).longValueExact());
		taskRecord.setRequestprice(price);
		taskRecord.setFeebytalk(feeByTalk);

		dslContext.attach(taskRecord);
		taskRecord.store();

		logger.info("{} generated. userId = {}", dexTaskId, userId);

		// calculate fee
		CalculateFeeResult feeResult;
		try {
			feeResult = feeCalculationService.calculateOfferFee(!taskType.equals(DexTaskTypeEnum.OFFER_CREATE_BUY), sellAssetCode, buyAssetCode, amount, price, feeByTalk);
		} catch(Exception ex) {
			logger.error("{} failed. : {} {}", dexTaskId, ex.getClass().getSimpleName(), ex.getMessage());

			taskRecord.setErrorposition("calculate fee");
			taskRecord.setErrorcode(ex.getClass().getSimpleName());
			taskRecord.setErrormessage(ex.getMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw ex;
		}

		// build encData for CreateOffer request
		RelayEncryptedContent<StellarRawTxInfo> encData;
		try {
			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(sourceAccountId);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source.getAccountId());

			// get assetType
			Asset buyAssetType = maService.getAssetType(buyAssetCode);

			Transaction.Builder txBuilder = new Transaction.Builder(sourceAccount, stellarNetworkService.getNetwork())
					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
					.setOperationFee(stellarNetworkService.getNetworkFee())
					.addMemo(Memo.text(dexTaskId.getId()));

			if(feeResult.getFeeAmountRaw().compareTo(BigInteger.ZERO) > 0) {
				// build fee operation
				txBuilder.addOperation(
						new PaymentOperation
								.Builder(feeResult.getFeeHolderAccountAddress(), feeResult.getFeeAssetType(), StellarConverter.rawToActualString(feeResult.getFeeAmountRaw()))
								.build()
				);
			}

			switch(taskType) {
				case OFFER_CREATE_SELL:
					// build manage offer operation
					txBuilder.addOperation(
							new ManageSellOfferOperation
									.Builder(feeResult.getSellAssetType(), buyAssetType, StellarConverter.rawToActualString(feeResult.getSellAmountRaw()), StellarConverter.actualToString(price)).setOfferId(0)
									.build()
					);
					break;
				case OFFER_CREATE_BUY:
					// build manage offer operation
					txBuilder.addOperation(
							new ManageBuyOfferOperation
									.Builder(feeResult.getSellAssetType(), buyAssetType, StellarConverter.rawToActualString(feeResult.getBuyAmountRaw()), StellarConverter.actualToString(price)).setOfferId(0)
									.build()
					);
					break;
				default:
					throw new IllegalArgumentException("TaskType " + taskType + " is not supported by createOffer");
			}


			// build tx
			StellarRawTxInfo stellarRawTxInfo = StellarRawTxInfo.build(txBuilder.build());

			encData = new RelayEncryptedContent<>(stellarRawTxInfo);

			taskRecord.setSellamountraw(feeResult.getSellAmountRaw().longValueExact());
			taskRecord.setBuyamountraw(feeResult.getBuyAmountRaw().longValueExact());
			taskRecord.setFeeassetcode(feeResult.getFeeAssetCode());
			taskRecord.setFeeamountraw(feeResult.getFeeAmountRaw().longValueExact());
			taskRecord.setFeecollectaccount(feeResult.getFeeHolderAccountAddress());
			taskRecord.setTxSeq(stellarRawTxInfo.getSequence());
			taskRecord.setTxHash(stellarRawTxInfo.getHash());
			taskRecord.setTxXdr(stellarRawTxInfo.getEnvelopeXdr());
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
		encData.addDescription("type", taskType.name());
		encData.addDescription("tradeWalletAddress", taskRecord.getSourceaccount());
		encData.addDescription("sellAssetCode", taskRecord.getSellassetcode());
		encData.addDescription("buyAssetCode", taskRecord.getBuyassetcode());
		encData.addDescription("amount", StellarConverter.rawToActualString(taskRecord.getRequestamountraw()));
		encData.addDescription("price", taskRecord.getRequestprice().stripTrailingZeros().toPlainString());
		encData.addDescription("sellAmount", StellarConverter.rawToActualString(taskRecord.getSellamountraw()));
		encData.addDescription("buyAmount", StellarConverter.rawToActualString(taskRecord.getBuyamountraw()));
		encData.addDescription("feeAssetCode", taskRecord.getFeeassetcode());
		encData.addDescription("feeAmount", StellarConverter.rawToActualString(taskRecord.getFeeamountraw()));

		// send relay addContents request
		IntegrationResult<RelayAddContentsResponse> relayResult = relayServerService.requestAddContents(RelayMsgTypeEnum.CREATEOFFER, userId, dexTaskId, encData);

		if(!relayResult.isSuccess()) {
			logger.error("{} failed. {}", dexTaskId, relayResult);

			taskRecord.setErrorposition("request relay");
			taskRecord.setErrorcode(relayResult.getErrorCode());
			taskRecord.setErrormessage(relayResult.getErrorMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new IntegrationException(relayResult);
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
		result.setAmount(amount);
		result.setPrice(price);
		result.setFeeResult(feeResult);
		return result;
	}

	public DexKeyResult createOfferDexKey(Long userId, String taskId, String transId, String signature) throws TaskIntegrityCheckFailedException, TaskNotFoundException, SignatureVerificationFailedException {
		DexTaskId dexTaskId = DexTaskId.decode_taskId(taskId);
		if(!dexTaskId.getType().equals(DexTaskTypeEnum.OFFER_CREATE_BUY) && !dexTaskId.getType().equals(DexTaskTypeEnum.OFFER_CREATE_SELL))
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

	public DeleteOfferResult deleteOffer(long userId, long offerId, DexTaskTypeEnum taskType, String sourceAccountId, String sellAssetCode, String buyAssetCode, BigDecimal price) throws TokenMetaNotFoundException, StellarException, IntegrationException {
		if(!(taskType != null && (taskType.equals(DexTaskTypeEnum.OFFER_DELETE_BUY) || taskType.equals(DexTaskTypeEnum.OFFER_DELETE_SELL)))) {
			throw new IllegalArgumentException("TaskType " + taskType + " is not supported by deleteOffer");
		}


		DexTaskId dexTaskId = DexTaskId.generate_taskId(taskType);

		// create task record
		DexTaskDeleteofferRecord taskRecord = new DexTaskDeleteofferRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);
		taskRecord.setTasktype(taskType);
		taskRecord.setSourceaccount(sourceAccountId);
		taskRecord.setOfferid(offerId);
		taskRecord.setSellassetcode(sellAssetCode);
		taskRecord.setBuyassetcode(buyAssetCode);
		taskRecord.setPrice(price);
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
		RelayEncryptedContent<StellarRawTxInfo> encData;
		try {
			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(sourceAccountId);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source.getAccountId());

			// get assetType
			Asset sellAssetType = maService.getAssetType(sellAssetCode);
			Asset buyAssetType = maService.getAssetType(buyAssetCode);

			Transaction.Builder txBuilder = new Transaction.Builder(sourceAccount, stellarNetworkService.getNetwork())
					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
					.setOperationFee(stellarNetworkService.getNetworkFee())
					.addMemo(Memo.text(dexTaskId.getId()));

			// build manage offer operation

			switch(taskType) {
				case OFFER_DELETE_SELL:
					// build manage offer operation
					txBuilder.addOperation(
							new ManageSellOfferOperation
									.Builder(sellAssetType, buyAssetType, "0", StellarConverter.actualToString(price))
									.setOfferId(offerId)
									.build()
					);
					break;
				case OFFER_DELETE_BUY:
					// build manage offer operation
					txBuilder.addOperation(
							new ManageBuyOfferOperation
									.Builder(sellAssetType, buyAssetType, "0", StellarConverter.actualToString(price))
									.setOfferId(offerId)
									.build()
					);
					break;
				default:
					throw new IllegalArgumentException("TaskType " + taskType + " is not supported by deleteOffer");
			}

			// build tx
			StellarRawTxInfo stellarRawTxInfo = StellarRawTxInfo.build(txBuilder.build());

			encData = new RelayEncryptedContent<>(stellarRawTxInfo);

			taskRecord.setTxSeq(stellarRawTxInfo.getSequence());
			taskRecord.setTxHash(stellarRawTxInfo.getHash());
			taskRecord.setTxXdr(stellarRawTxInfo.getEnvelopeXdr());
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
		encData.addDescription("type", taskType.name());
		encData.addDescription("tradeWalletAddress", taskRecord.getSourceaccount());
		encData.addDescription("offerId", Long.toString(taskRecord.getOfferid()));
		encData.addDescription("sellAssetCode", taskRecord.getSellassetcode());
		encData.addDescription("buyAssetCode", taskRecord.getBuyassetcode());
		encData.addDescription("price", taskRecord.getPrice().stripTrailingZeros().toPlainString());

		// send relay addContents request
		IntegrationResult<RelayAddContentsResponse> relayResult = relayServerService.requestAddContents(RelayMsgTypeEnum.DELETEOFFER, userId, dexTaskId, encData);

		if(!relayResult.isSuccess()) {
			logger.error("{} failed. {}", dexTaskId, relayResult);

			taskRecord.setErrorposition("request relay");
			taskRecord.setErrorcode(relayResult.getErrorCode());
			taskRecord.setErrormessage(relayResult.getErrorMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new IntegrationException(relayResult);
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
		result.setTaskType(taskType);
		result.setOfferId(taskRecord.getOfferid());
		result.setSellAssetCode(taskRecord.getSellassetcode());
		result.setBuyAssetCode(taskRecord.getBuyassetcode());
		result.setPrice(price);
		return result;
	}

	public DexKeyResult deleteOfferDexKey(Long userId, String taskId, String transId, String signature) throws TaskIntegrityCheckFailedException, TaskNotFoundException, SignatureVerificationFailedException {
		DexTaskId dexTaskId = DexTaskId.decode_taskId(taskId);
		if(!dexTaskId.getType().equals(DexTaskTypeEnum.OFFER_DELETE_BUY) || !dexTaskId.getType().equals(DexTaskTypeEnum.OFFER_DELETE_SELL))
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
