package io.talken.dex.api.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import io.talken.common.exception.common.RestApiErrorException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskAnchorRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskDeanchorRecord;
import io.talken.common.util.JSONWriter;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.integration.RestApiResult;
import io.talken.dex.api.controller.dto.*;
import io.talken.dex.api.service.integration.anchor.*;
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

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_ANCHOR;
import static io.talken.common.persistence.jooq.Tables.DEX_TASK_DEANCHOR;


// FIXME : disabled before applying stellar-sdk 0.9.0
@Service
@Scope("singleton")
public class AnchorService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AnchorService.class);

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private AnchorServerService anchorServerService;

	@Autowired
	private RelayServerService relayServerService;

	@Autowired
	private TokenMetaService tmService;

	@Autowired
	private FeeCalculationService feeCalculationService;

	@Autowired
	private DSLContext dslContext;

	public AnchorResult anchor(long userId, String privateWalletAddress, String tradeWalletAddress, String assetCode, BigDecimal amount, AnchorRequest ancRequestBody) throws TokenMetaNotFoundException, RestApiErrorException, ActiveAssetHolderAccountNotFoundException, InternalServerErrorException {
		String assetHolderAddress = tmService.getActiveHolderAccountAddress(assetCode);

		DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.ANCHOR);

		// create task record
		DexTaskAnchorRecord taskRecord = new DexTaskAnchorRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setPrivateaddr(privateWalletAddress);
		taskRecord.setTradeaddr(tradeWalletAddress);
		taskRecord.setHolderaddr(assetHolderAddress);
		taskRecord.setAssetcode(assetCode);
		taskRecord.setAmountraw(StellarConverter.actualToRaw(amount).longValueExact());

		dslContext.attach(taskRecord);
		taskRecord.store();

		logger.info("{} generated. userId = {}", dexTaskId, userId);

		AncServerAnchorRequest req = new AncServerAnchorRequest();
		req.setTaskId(dexTaskId.getId());
		req.setUid(String.valueOf(userId));
		req.setFrom(privateWalletAddress);
		req.setTo(assetHolderAddress);
		req.setStellar(tradeWalletAddress);
		req.setSymbol(assetCode);
		req.setValue(amount.floatValue());
		req.setMemo(UTCUtil.getNow().toString());

		// request anchor monitor
		RestApiResult<AncServerAnchorResponse> anchorResult = anchorServerService.requestAnchor(req);
		if(!anchorResult.isSuccess()) {
			// update task record
			logger.error("{} failed. : {}", dexTaskId, anchorResult.toString());
			taskRecord.setErrorposition("request anchor");
			taskRecord.setErrorcode(anchorResult.getErrorCode());
			taskRecord.setErrormessage(JSONWriter.toJsonStringSafe(anchorResult.getData()));
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new RestApiErrorException(anchorResult);
		}

		// update task record
		taskRecord.setAncIndex(anchorResult.getData().getData().getIndex());
		taskRecord.update();


		// build relay contents
		ancRequestBody.setTo(assetHolderAddress);
		RelayEncryptedContent<AnchorRequest> encData;
		try {
			encData = new RelayEncryptedContent<>(ancRequestBody);
		} catch(JsonProcessingException | GeneralSecurityException e) {
			logger.error("{} failed. {} {}", dexTaskId, e.getClass().getSimpleName(), e.getMessage());

			taskRecord.setErrorposition("encrypt data");
			taskRecord.setErrorcode(e.getClass().getSimpleName());
			taskRecord.setErrormessage(e.getMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new InternalServerErrorException(e);
		}

		// set trans description
		encData.addDescription("privateWalletAddress", taskRecord.getPrivateaddr());
		encData.addDescription("tradeWalletAddress", taskRecord.getTradeaddr());
		encData.addDescription("assetHolderAddress", taskRecord.getHolderaddr());
		encData.addDescription("assetCode", taskRecord.getAssetcode());
		encData.addDescription("amount", StellarConverter.rawToActualString(BigInteger.valueOf(taskRecord.getAmountraw())));

		// send relay addContents request
		RestApiResult<RelayAddContentsResponse> relayResult = relayServerService.requestAddContents(RelayMsgTypeEnum.ANCHOR, userId, dexTaskId, encData);

		if(!relayResult.isSuccess()) {
			logger.error("{} failed. {}", dexTaskId, relayResult);

			taskRecord.setErrorposition("request relay");
			taskRecord.setErrorcode(String.valueOf(relayResult.getResponseCode()));
			taskRecord.setErrormessage(relayResult.getErrorMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new RestApiErrorException(relayResult);
		}

		// update task record
		taskRecord.setRlyDexkey(encData.getKey());
		taskRecord.setRlyTransid(relayResult.getData().getTransId());
		taskRecord.setRlyRegdt(relayResult.getData().getRegDt());
		taskRecord.setRlyEnddt(relayResult.getData().getEndDt());
		taskRecord.setSuccessFlag(true);
		taskRecord.update();

		logger.debug("{} complete.", dexTaskId);


		AnchorResult result = new AnchorResult();
		result.setTaskId(dexTaskId.getId());
		result.setHolderAccountAddress(assetHolderAddress);
		result.setTransId(taskRecord.getRlyTransid());
		result.setAssetCode(taskRecord.getAssetcode());
		result.setAmount(StellarConverter.rawToActual(taskRecord.getAmountraw()));
		return result;
	}

	public DexKeyResult anchorDexKey(Long userId, String taskId, String transId, String signature) throws TaskIntegrityCheckFailedException, TaskNotFoundException, SignatureVerificationFailedException {
		DexTaskId dexTaskId = DexTaskId.decode_taskId(taskId);
		if(!dexTaskId.getType().equals(DexTaskTypeEnum.ANCHOR))
			throw new TaskNotFoundException(taskId);

		DexTaskAnchorRecord taskRecord = dslContext.selectFrom(DEX_TASK_ANCHOR)
				.where(DEX_TASK_ANCHOR.TASKID.eq(taskId))
				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskId));

		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskId);
		if(!taskRecord.getRlyTransid().equals(transId)) throw new TaskIntegrityCheckFailedException(taskId);

		if(!StellarSignVerifier.verifySignBase64(taskRecord.getTradeaddr(), transId, signature))
			throw new SignatureVerificationFailedException(transId, signature);

		DexKeyResult result = new DexKeyResult();
		result.setDexKey(taskRecord.getRlyDexkey());
		return result;
	}

	public DeanchorResult deanchor(long userId, String privateWalletAddress, String tradeWalletAddress, String assetCode, BigDecimal amount, Boolean feeByTalk) throws TokenMetaNotFoundException, StellarException, RestApiErrorException, AssetConvertException, EffectiveAmountIsNegativeException {
		DexTaskId dexTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.DEANCHOR);

		amount = StellarConverter.scale(amount);

		// create task record
		DexTaskDeanchorRecord taskRecord = new DexTaskDeanchorRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setPrivateaddr(privateWalletAddress);
		taskRecord.setTradeaddr(tradeWalletAddress);
		taskRecord.setAssetcode(assetCode);
		taskRecord.setAmountraw(StellarConverter.actualToRaw(amount).longValueExact());
		taskRecord.setFeebyctx(feeByTalk);

		dslContext.attach(taskRecord);
		taskRecord.store();

		logger.info("{} generated. userId = {}", dexTaskId, userId);

		// calculate fee
		CalculateFeeResult feeResult;
		try {
			feeResult = feeCalculationService.calculateDeanchorFee(assetCode, amount, feeByTalk);
		} catch(Exception ex) {
			logger.error("{} failed. : {} {}", dexTaskId, ex.getClass().getSimpleName(), ex.getMessage());

			taskRecord.setErrorposition("calculate fee");
			taskRecord.setErrorcode(ex.getClass().getSimpleName());
			taskRecord.setErrormessage(ex.getMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw ex;
		}

		// build encData for deanchor request
		RelayEncryptedContent<StellarRawTxInfo> encData;
		try {
			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(tradeWalletAddress);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source.getAccountId());

			KeyPair baseAccount = tmService.getBaseAccount(assetCode);

			Transaction.Builder txBuilder = new Transaction.Builder(sourceAccount, stellarNetworkService.getNetwork())
					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
					.setOperationFee(stellarNetworkService.getNetworkFee())
					.addMemo(Memo.text(dexTaskId.getId()));

			// build fee operation
			if(feeResult.getFeeAmountRaw().compareTo(BigInteger.ZERO) > 0) {
				txBuilder.addOperation(
						new PaymentOperation
								.Builder(feeResult.getFeeHolderAccountAddress(), feeResult.getFeeAssetType(), StellarConverter.rawToActualString(feeResult.getFeeAmountRaw()))
								.build()
				);
			}

			// build deanchor operation
			txBuilder.addOperation(
					new PaymentOperation
							.Builder(baseAccount.getAccountId(), feeResult.getSellAssetType(), StellarConverter.rawToActualString(feeResult.getSellAmountRaw()))
							.build()
			);

			// build tx
			StellarRawTxInfo stellarRawTxInfo = StellarRawTxInfo.build(txBuilder.build());

			encData = new RelayEncryptedContent<>(stellarRawTxInfo);

			taskRecord.setBaseaccount(baseAccount.getAccountId());
			taskRecord.setDeanchoramountraw(feeResult.getSellAmountRaw().longValueExact());
			taskRecord.setFeeamountraw(feeResult.getFeeAmountRaw().longValueExact());
			taskRecord.setFeeassettype(StellarConverter.toAssetCode(feeResult.getFeeAssetType()));
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

		// request deanchor monitor
		AncServerDeanchorRequest ancRequest = new AncServerDeanchorRequest();
		ancRequest.setTaskId(dexTaskId.getId());
		ancRequest.setUid(String.valueOf(userId));
		ancRequest.setSymbol(taskRecord.getAssetcode());
		ancRequest.setHash(taskRecord.getTxHash());
		ancRequest.setFrom(taskRecord.getTradeaddr());
		ancRequest.setTo(taskRecord.getBaseaccount());
		ancRequest.setAddress(taskRecord.getPrivateaddr());
		ancRequest.setValue(StellarConverter.rawToActual(taskRecord.getDeanchoramountraw()).doubleValue());
		ancRequest.setMemo(UTCUtil.getNow().toString());

		RestApiResult<AncServerDeanchorResponse> deanchorResult = anchorServerService.requestDeanchor(ancRequest);

		if(!deanchorResult.isSuccess()) {
			logger.error("{} failed. {}", dexTaskId, deanchorResult);

			taskRecord.setErrorposition("request deanchor");
			taskRecord.setErrorcode(deanchorResult.getErrorCode());
			taskRecord.setErrormessage(deanchorResult.getErrorMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new RestApiErrorException(deanchorResult);
		}

		// update task record
		taskRecord.setAncIndex(deanchorResult.getData().getData().getIndex());
		taskRecord.update();


		// set trans description
		encData.addDescription("privateWalletAddress", taskRecord.getPrivateaddr());
		encData.addDescription("tradeWalletAddress", taskRecord.getTradeaddr());
		encData.addDescription("baseAccountAddress", taskRecord.getBaseaccount());
		encData.addDescription("assetCode", taskRecord.getAssetcode());
		encData.addDescription("amount", StellarConverter.rawToActualString(taskRecord.getAmountraw()));
		encData.addDescription("feeAssetCode", taskRecord.getFeeassettype());
		encData.addDescription("feeAmount", StellarConverter.rawToActualString(taskRecord.getFeeamountraw()));

		// send relay addContents request
		RestApiResult<RelayAddContentsResponse> relayResult = relayServerService.requestAddContents(RelayMsgTypeEnum.DEANCHOR, userId, dexTaskId, encData);

		if(!relayResult.isSuccess()) {
			logger.error("{} failed. {}", dexTaskId, relayResult);

			taskRecord.setErrorposition("request relay");
			taskRecord.setErrorcode(String.valueOf(relayResult.getResponseCode()));
			taskRecord.setErrormessage(relayResult.getErrorMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new RestApiErrorException(relayResult);
		}

		// update task record
		taskRecord.setRlyDexkey(encData.getKey());
		taskRecord.setRlyTransid(relayResult.getData().getTransId());
		taskRecord.setRlyRegdt(relayResult.getData().getRegDt());
		taskRecord.setRlyEnddt(relayResult.getData().getEndDt());
		taskRecord.setSuccessFlag(true);
		taskRecord.update();

		logger.debug("{} complete.", dexTaskId);

		DeanchorResult result = new DeanchorResult();
		result.setTaskId(dexTaskId.getId());
		result.setTransId(taskRecord.getRlyTransid());
		result.setFeeAssetCode(taskRecord.getFeeassettype());
		result.setFeeAmount(StellarConverter.rawToActual(taskRecord.getFeeamountraw()));
		result.setDeanchorAssetCode(taskRecord.getAssetcode());
		result.setDeanchorAmount(StellarConverter.rawToActual(taskRecord.getDeanchoramountraw()));
		return result;
	}

	public DexKeyResult deanchorDexKey(Long userId, String taskId, String transId, String signature) throws TaskIntegrityCheckFailedException, TaskNotFoundException, SignatureVerificationFailedException {
		DexTaskId dexTaskId = DexTaskId.decode_taskId(taskId);
		if(!dexTaskId.getType().equals(DexTaskTypeEnum.DEANCHOR))
			throw new TaskNotFoundException(taskId);

		DexTaskDeanchorRecord taskRecord = dslContext.selectFrom(DEX_TASK_DEANCHOR)
				.where(DEX_TASK_DEANCHOR.TASKID.eq(taskId))
				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskId));

		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskId);
		if(!taskRecord.getRlyTransid().equals(transId)) throw new TaskIntegrityCheckFailedException(taskId);

		if(!StellarSignVerifier.verifySignBase64(taskRecord.getTradeaddr(), transId, signature))
			throw new SignatureVerificationFailedException(transId, signature);

		DexKeyResult result = new DexKeyResult();
		result.setDexKey(taskRecord.getRlyDexkey());
		return result;
	}
}
