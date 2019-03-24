package io.colligence.talken.dex.api.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import io.colligence.talken.common.persistence.enums.DexTaskTypeEnum;
import io.colligence.talken.common.persistence.jooq.tables.records.DexAnchorTaskRecord;
import io.colligence.talken.common.persistence.jooq.tables.records.DexDeanchorTaskRecord;
import io.colligence.talken.common.util.JSONWriter;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.common.util.UTCUtil;
import io.colligence.talken.dex.api.dto.AnchorRequest;
import io.colligence.talken.dex.api.dto.AnchorResult;
import io.colligence.talken.dex.api.dto.DeanchorResult;
import io.colligence.talken.dex.api.dto.DexKeyResult;
import io.colligence.talken.dex.exception.*;
import io.colligence.talken.dex.service.integration.APIResult;
import io.colligence.talken.dex.service.integration.anchor.*;
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

import static io.colligence.talken.common.persistence.jooq.Tables.DEX_ANCHOR_TASK;
import static io.colligence.talken.common.persistence.jooq.Tables.DEX_DEANCHOR_TASK;

@Service
@Scope("singleton")
public class AnchorService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AnchorService.class);

	@Autowired
	private DexTaskIdService taskIdService;

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

	public AnchorResult anchor(long userId, String privateWalletAddress, String tradeWalletAddress, String assetCode, Double amount, AnchorRequest ancRequestBody) throws TokenMetaDataNotFoundException, APIErrorException, ActiveAssetHolderAccountNotFoundException, InternalServerErrorException {
		String assetHolderAddress = tmService.getActiveHolderAccountAddress(assetCode);

		DexTaskId dexTaskId = taskIdService.generate_taskId(DexTaskTypeEnum.ANCHOR);

		// create task record
		DexAnchorTaskRecord taskRecord = new DexAnchorTaskRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);
		taskRecord.setStep(1);
		taskRecord.setFinishFlag(false);

		taskRecord.setPrivateaddr(privateWalletAddress);
		taskRecord.setTradeaddr(tradeWalletAddress);
		taskRecord.setHolderaddr(assetHolderAddress);
		taskRecord.setAssetcode(assetCode);
		taskRecord.setAmountraw(StellarConverter.doubleToRaw(amount));

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
		APIResult<AncServerAnchorResponse> anchorResult = anchorServerService.requestAnchor(req);
		if(!anchorResult.isSuccess()) {
			// update task record
			logger.error("{} failed. : {}", dexTaskId, anchorResult.toString());
			taskRecord.setErrorposition("request anchor");
			taskRecord.setErrorcode(anchorResult.getErrorCode());
			taskRecord.setErrormessage(JSONWriter.toJsonStringSafe(anchorResult.getData()));
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new APIErrorException(anchorResult);
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

		// send relay addContents request
		APIResult<RelayAddContentsResponse> relayResult = relayServerService.requestAddContents(RelayMsgTypeEnum.ANCHOR, userId, dexTaskId, encData);

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

		logger.debug("{} complete.", dexTaskId);


		AnchorResult result = new AnchorResult();
		result.setTaskId(dexTaskId.getId());
		result.setHolderAccountAddress(assetHolderAddress);
		result.setTransId(taskRecord.getRlyTransid());
		result.setAssetCode(taskRecord.getAssetcode());
		result.setAmount(StellarConverter.rawToDouble(taskRecord.getAmountraw()));
		return result;
	}

	public DexKeyResult anchorDexKey(Long userId, String taskId, String transId, String signature) throws TaskIntegrityCheckFailedException, TaskNotFoundException, SignatureVerificationFailedException {
		DexTaskId dexTaskId = taskIdService.decode_taskId(taskId);
		if(!dexTaskId.getType().equals(DexTaskTypeEnum.ANCHOR))
			throw new TaskNotFoundException(taskId);

		DexAnchorTaskRecord taskRecord = dslContext.selectFrom(DEX_ANCHOR_TASK)
				.where(DEX_ANCHOR_TASK.TASKID.eq(taskId))
				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskId));

		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskId);
		if(!taskRecord.getRlyTransid().equals(transId)) throw new TaskIntegrityCheckFailedException(taskId);

		if(!StellarSignVerifier.verifySignBase64(taskRecord.getTradeaddr(), transId, signature))
			throw new SignatureVerificationFailedException(transId, signature);

		DexKeyResult result = new DexKeyResult();
		result.setDexKey(taskRecord.getRlyDexkey());
		return result;
	}

	public DeanchorResult deanchor(long userId, String privateWalletAddress, String tradeWalletAddress, String assetCode, Double amount, Boolean feeByCtx) throws TokenMetaDataNotFoundException, StellarException, APIErrorException, AssetConvertException, TokenMetaLoadException {
		DexTaskId dexTaskId = taskIdService.generate_taskId(DexTaskTypeEnum.DEANCHOR);

		// create task record
		DexDeanchorTaskRecord taskRecord = new DexDeanchorTaskRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setPrivateaddr(privateWalletAddress);
		taskRecord.setTradeaddr(tradeWalletAddress);
		taskRecord.setAssetcode(assetCode);
		taskRecord.setAmountraw(StellarConverter.doubleToRaw(amount));
		taskRecord.setFeebyctx(feeByCtx);

		dslContext.attach(taskRecord);
		taskRecord.store();

		logger.info("{} generated. userId = {}", dexTaskId, userId);

		// calculate fee
		FeeCalculationService.Fee fee;
		try {
			fee = feeCalculationService.calculateDeanchorFee(assetCode, StellarConverter.doubleToRaw(amount), feeByCtx);
		} catch(TokenMetaLoadException ex) {
			logger.error("{} failed. : {} {}", dexTaskId, ex.getClass().getSimpleName(), ex.getMessage());

			taskRecord.setErrorposition("calculate fee");
			taskRecord.setErrorcode(ex.getClass().getSimpleName());
			taskRecord.setErrormessage(ex.getMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw ex;
		}

		// build encData for deanchor request
		RelayEncryptedContent<BareTxInfo> encData;
		try {
			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(tradeWalletAddress);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source);

			KeyPair baseAccount = tmService.getBaseAccount(assetCode);

			Transaction.Builder txBuilder = stellarNetworkService.getTransactionBuilderFor(sourceAccount)
					.addMemo(Memo.text(dexTaskId.getId()));

			// build fee operation
			if(fee.getFeeAmountRaw() > 0) {
				txBuilder.addOperation(
						new PaymentOperation
								.Builder(fee.getFeeCollectorAccount(), fee.getFeeAssetType(), StellarConverter.rawToDoubleString(fee.getFeeAmountRaw()))
								.build()
				);
			}

			// build deanchor operation
			txBuilder.addOperation(
					new PaymentOperation
							.Builder(baseAccount, fee.getSellAssetType(), StellarConverter.rawToDoubleString(fee.getSellAmountRaw()))
							.build()
			);

			// build tx
			BareTxInfo bareTxInfo = BareTxInfo.build(txBuilder.build());

			encData = new RelayEncryptedContent<>(bareTxInfo);

			taskRecord.setBaseaccount(baseAccount.getAccountId());
			taskRecord.setDeanchoramountraw(fee.getSellAmountRaw());
			taskRecord.setFeeamountraw(fee.getFeeAmountRaw());
			taskRecord.setFeeassettype(StellarConverter.toAssetCode(fee.getFeeAssetType()));
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

		// request deanchor monitor
		AncServerDeanchorRequest ancRequest = new AncServerDeanchorRequest();
		ancRequest.setTaskId(dexTaskId.getId());
		ancRequest.setUid(String.valueOf(userId));
		ancRequest.setSymbol(taskRecord.getAssetcode());
		ancRequest.setHash(taskRecord.getTxHash());
		ancRequest.setFrom(taskRecord.getTradeaddr());
		ancRequest.setTo(taskRecord.getBaseaccount());
		ancRequest.setAddress(taskRecord.getPrivateaddr());
		ancRequest.setValue(StellarConverter.rawToDouble(taskRecord.getDeanchoramountraw()));
		ancRequest.setMemo(UTCUtil.getNow().toString());

		APIResult<AncServerDeanchorResponse> deanchorResult = anchorServerService.requestDeanchor(ancRequest);

		if(!deanchorResult.isSuccess()) {
			logger.error("{} failed. {}", dexTaskId, deanchorResult);

			taskRecord.setErrorposition("request deanchor");
			taskRecord.setErrorcode(deanchorResult.getErrorCode());
			taskRecord.setErrormessage(deanchorResult.getErrorMessage());
			taskRecord.setSuccessFlag(false);
			taskRecord.update();

			throw new APIErrorException(deanchorResult);
		}

		// update task record
		taskRecord.setAncIndex(deanchorResult.getData().getData().getIndex());
		taskRecord.update();


		// send relay addContents request
		APIResult<RelayAddContentsResponse> relayResult = relayServerService.requestAddContents(RelayMsgTypeEnum.DEANCHOR, userId, dexTaskId, encData);

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

		logger.debug("{} complete.", dexTaskId);

		DeanchorResult result = new DeanchorResult();
		result.setTaskId(dexTaskId.getId());
		result.setTransId(taskRecord.getRlyTransid());
		result.setFeeAssetCode(taskRecord.getFeeassettype());
		result.setFeeAmount(StellarConverter.rawToDouble(taskRecord.getFeeamountraw()));
		result.setDeanchorAssetCode(taskRecord.getAssetcode());
		result.setDeanchorAmount(StellarConverter.rawToDouble(taskRecord.getDeanchoramountraw()));
		return result;
	}

	public DexKeyResult deanchorDexKey(Long userId, String taskId, String transId, String signature) throws TaskIntegrityCheckFailedException, TaskNotFoundException, SignatureVerificationFailedException {
		DexTaskId dexTaskId = taskIdService.decode_taskId(taskId);
		if(!dexTaskId.getType().equals(DexTaskTypeEnum.DEANCHOR))
			throw new TaskNotFoundException(taskId);

		DexDeanchorTaskRecord taskRecord = dslContext.selectFrom(DEX_DEANCHOR_TASK)
				.where(DEX_DEANCHOR_TASK.TASKID.eq(taskId))
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
