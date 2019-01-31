package io.colligence.talken.dex.api.dex.anchor;


import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.colligence.talken.common.persistence.jooq.tables.records.DexAnchorTaskRecord;
import io.colligence.talken.common.persistence.jooq.tables.records.DexDeanchorTaskRecord;
import io.colligence.talken.common.util.JSONWriter;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.common.util.UTCUtil;
import io.colligence.talken.dex.DexSettings;
import io.colligence.talken.dex.api.DexTaskId;
import io.colligence.talken.dex.api.dex.FeeCalculationService;
import io.colligence.talken.dex.api.dex.TxEncryptedData;
import io.colligence.talken.dex.api.dex.TxInformation;
import io.colligence.talken.dex.api.dex.anchor.dto.AnchorResult;
import io.colligence.talken.dex.api.dex.anchor.dto.DeanchorDexKeyResult;
import io.colligence.talken.dex.api.dex.anchor.dto.DeanchorResult;
import io.colligence.talken.dex.api.mas.ma.ManagedAccountService;
import io.colligence.talken.dex.exception.*;
import io.colligence.talken.dex.service.integration.APIResult;
import io.colligence.talken.dex.service.integration.anchor.*;
import io.colligence.talken.dex.service.integration.relay.RelayAddContentsRequest;
import io.colligence.talken.dex.service.integration.relay.RelayAddContentsResponse;
import io.colligence.talken.dex.service.integration.relay.RelayServerService;
import io.colligence.talken.dex.service.integration.stellar.StellarNetworkService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;

import static io.colligence.talken.common.persistence.jooq.Tables.DEX_ANCHOR_TASK;
import static io.colligence.talken.common.persistence.jooq.Tables.DEX_DEANCHOR_TASK;

@Service
@Scope("singleton")
public class AnchorService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AnchorService.class);

	@Autowired
	private DexSettings dexSettings;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private AnchorServerService anchorServerService;

	@Autowired
	private RelayServerService relayServerService;

	@Autowired
	private ManagedAccountService maService;

	@Autowired
	private FeeCalculationService feeCalculationService;

	@Autowired
	private DSLContext dslContext;

	public AnchorResult anchor(long userId, String privateWalletAddress, String tradeWalletAddress, String assetCode, Double amount) throws AssetTypeNotFoundException, APIErrorException, ActiveAssetHolderAccountNotFoundException {
		String assetHolderAddress = maService.getActiveHolderAccountAddress(assetCode);

		DexTaskId dexTaskId = DexTaskId.generate(DexTaskId.Type.ANCHOR);

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
		taskRecord.setAmount(amount);

		dslContext.attach(taskRecord);
		taskRecord.store();

		logger.debug("{} generated. userId = {}", dexTaskId, userId);

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
		taskRecord.setSuccessFlag(true);
		taskRecord.update();
		logger.debug("{} complete.", dexTaskId);

		AnchorResult result = new AnchorResult();
		result.setTaskId(dexTaskId.getId());
		result.setHolderAccountAddress(assetHolderAddress);
		return result;
	}

//	@Deprecated
//	public AnchorSubmitResult submitAnchorTransaction(long userId, String taskID, String assetCode, String txData) throws APIErrorException, TaskNotFoundException, TaskIntegrityCheckFailedException, AssetTypeNotFoundException {
//
//		DexTaskId dexTaskId = DexTaskId.fromId(taskID);
//		DexAnchorTaskRecord taskRecord = dslContext.selectFrom(DEX_ANCHOR_TASK)
//				.where(DEX_ANCHOR_TASK.TASKID.eq(taskID))
//				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskID));
//
//		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskID);
//		if(!taskRecord.getS1iAssetcode().equalsIgnoreCase(assetCode)) throw new TaskIntegrityCheckFailedException(taskID);
//
//		// update task record as step 2
//		taskRecord.setStep(2);
//		taskRecord.setS2iTxdata(txData);
//		taskRecord.update();
//
//		TxtServerRequest request = new TxtServerRequest();
//		request.setServiceId(dexSettings.getServer().getTxtServerId());
//		request.setTaskId(taskID);
//		request.setSignatures(txData);
//
//		APIResult<TxtServerResponse> txtResult = txTunnelService.requestTxTunnel(maService.getAssetPlatform(assetCode), request);
//		if(!txtResult.isSuccess()) {
//			// update task record
//			logger.error("{} step 2 failed. : {}", dexTaskId, txtResult.toString());
//			taskRecord.setS2oSuccessFlag(false);
//			taskRecord.setS2oCode(txtResult.getErrorCode());
//			taskRecord.setS2oData(JSONWriter.toJsonStringSafe(txtResult.getData()));
//			taskRecord.setFinishFlag(true);
//			taskRecord.update();
//
//			throw new APIErrorException(txtResult);
//		}
//
//		// update task record
//		logger.debug("{} step 2 success.", dexTaskId);
//		taskRecord.setS2oCode(txtResult.getData().getCode());
//		taskRecord.setS2oMessage(txtResult.getData().getMessage());
//		taskRecord.setS2oTxid(txtResult.getData().getHash());
//		taskRecord.setS2oData(txtResult.getData().getPayload());
//		taskRecord.setS2oSuccessFlag(true);
//		taskRecord.setFinishFlag(true);
//		taskRecord.update();
//
//		return new AnchorSubmitResult(txtResult.getData());
//	}


	public DeanchorResult deanchor(long userId, String privateWalletAddress, String tradeWalletAddress, String assetCode, Double amount, Boolean feeByCtx) throws AssetTypeNotFoundException, StellarException, APIErrorException {
		DexTaskId dexTaskId = DexTaskId.generate(DexTaskId.Type.DEANCHOR);

		// create task record
		DexDeanchorTaskRecord taskRecord = new DexDeanchorTaskRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);

		taskRecord.setPrivateaddr(privateWalletAddress);
		taskRecord.setTradeaddr(tradeWalletAddress);
		taskRecord.setAssetcode(assetCode);
		taskRecord.setAmount(amount);
		taskRecord.setFeebyctx(feeByCtx);

		dslContext.attach(taskRecord);
		taskRecord.store();

		logger.debug("{} generated. userId = {}", dexTaskId, userId);


		// build encData for deanchor request
		TxEncryptedData encData;
		try {
			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(tradeWalletAddress);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source);

			KeyPair baseAccount = maService.getBaseAccount(assetCode);

			FeeCalculationService.Fee fee = feeCalculationService.calculateDeanchorFee(assetCode, amount, feeByCtx);

			Transaction.Builder txBuilder = new Transaction
					.Builder(sourceAccount)
					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
					.addMemo(Memo.text(dexTaskId.getId()));

			// build fee operation
			if(fee.getFeeAmount() > 0) {
				txBuilder.addOperation(
						new PaymentOperation
								.Builder(fee.getFeeCollectorAccount(), fee.getFeeAssetType(), Double.toString(fee.getFeeAmount()))
								.build()
				);
			}

			// build deanchor operation
			txBuilder.addOperation(
					new PaymentOperation
							.Builder(baseAccount, fee.getSellAssetType(), Double.toString(fee.getSellAmount()))
							.build()
			);

			// build tx
			TxInformation txInformation = TxInformation.buildTxInformation(txBuilder.build());

			encData = new TxEncryptedData(txInformation);

			taskRecord.setBaseaccount(baseAccount.getAccountId());
			taskRecord.setDeanchoramount(fee.getSellAmount());
			taskRecord.setFeeamount(fee.getFeeAmount());
			taskRecord.setFeeassettype(fee.getFeeAssetType().getType());
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

		// request deanchor monitor
		AncServerDeanchorRequest ancRequest = new AncServerDeanchorRequest();
		ancRequest.setTaskId(dexTaskId.getId());
		ancRequest.setSymbol(taskRecord.getAssetcode());
		ancRequest.setHash(taskRecord.getTxHash());
		ancRequest.setFrom(taskRecord.getTradeaddr());
		ancRequest.setTo(taskRecord.getBaseaccount());
		ancRequest.setAddress(taskRecord.getPrivateaddr());
		ancRequest.setValue(taskRecord.getDeanchoramount().floatValue());
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
		HashMap<String, String> contents = new HashMap<>();
		contents.put("taskId", dexTaskId.getId());
		contents.put("data", encData.getEncrypted());
		RelayAddContentsRequest rlyRequest = new RelayAddContentsRequest();
		rlyRequest.setMsgType("1004");
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

		DeanchorResult result = new DeanchorResult();
		result.setTaskId(dexTaskId.getId());
		result.setTransId(taskRecord.getRlyTransid());
		result.setFeeAssetType(taskRecord.getFeeassettype());
		result.setFeeAmount(taskRecord.getFeeamount());
		result.setDeanchorAssetType(taskRecord.getAssetcode());
		result.setDeanchorAmount(taskRecord.getDeanchoramount());
		return result;
	}

	public DeanchorDexKeyResult deanchorDexKey(Long userId, String taskId, String transId, String signature) throws TaskIntegrityCheckFailedException, TaskNotFoundException, SignatureVerificationFailedException {
		DexTaskId dexTaskId = DexTaskId.fromId(taskId);
		DexDeanchorTaskRecord taskRecord = dslContext.selectFrom(DEX_DEANCHOR_TASK)
				.where(DEX_ANCHOR_TASK.TASKID.eq(taskId))
				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskId));

		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskId);
		if(!taskRecord.getRlyTransid().equals(transId)) throw new TaskIntegrityCheckFailedException(taskId);

		byte[] ba_transId = transId.getBytes(StandardCharsets.UTF_8);
		byte[] ba_signature = ByteArrayUtil.hexStringToByteArray(signature);

		KeyPair kpFromPublicKey = KeyPair.fromAccountId(taskRecord.getTradeaddr());

		if(kpFromPublicKey.verify(ba_transId, ba_signature)) {
			DeanchorDexKeyResult result = new DeanchorDexKeyResult();
			result.setDexKey(taskRecord.getTxKey());
			return result;
		} else {
			throw new SignatureVerificationFailedException(transId, signature);
		}
	}

//	public DeanchorSubmitResult submitDeanchorTransaction(long userId, String taskID, String txHash, String txXdr) throws TaskIntegrityCheckFailedException, TaskNotFoundException, APIErrorException, TransactionHashNotMatchException {
//		DexTaskId dexTaskId = DexTaskId.fromId(taskID);
//		DexDeanchorTaskRecord taskRecord = dslContext.selectFrom(DEX_DEANCHOR_TASK)
//				.where(DEX_ANCHOR_TASK.TASKID.eq(taskID))
//				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskID));
//
//		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskID);
//		if(!taskRecord.getS1oTxhash().equalsIgnoreCase(txHash)) throw new TaskIntegrityCheckFailedException(taskID);
//
//		// update task step as 2
//		taskRecord.setStep(2);
//		taskRecord.update();
//
//		// request deanchor monitor
//		AncServerDeanchorRequest ancRequest = new AncServerDeanchorRequest();
//		ancRequest.setTaskId(taskID);
//		ancRequest.setSymbol(taskRecord.getS1iAssetcode());
//		ancRequest.setHash(txHash);
//		ancRequest.setFrom(taskRecord.getS1iTradeaddr());
//		ancRequest.setTo(taskRecord.getS1jBaseaccount());
//		ancRequest.setAddress(taskRecord.getS1iPrivateaddr());
//		ancRequest.setValue(taskRecord.getS1jDeanchoramount().floatValue());
//		ancRequest.setMemo(UTCUtil.getNow().toString());
//
//		APIResult<AncServerDeanchorResponse> deanchorResult = anchorServerService.requestDeanchor(ancRequest);
//		if(!deanchorResult.isSuccess()) {
//			logger.debug("{} step 2 failed.", dexTaskId, deanchorResult);
//
//			taskRecord.setS2oSuccessFlag(false);
//			taskRecord.setS2oCode(deanchorResult.getErrorCode());
//			taskRecord.setS2oData(JSONWriter.toJsonStringSafe(deanchorResult.getData()));
//			taskRecord.setFinishFlag(true);
//
//			throw new APIErrorException(deanchorResult);
//		}
//
//		// update task record
//		logger.debug("{} step 2 success.", dexTaskId);
//		taskRecord.setS2oSuccessFlag(true);
//		taskRecord.setS2oCode(Integer.toString(deanchorResult.getData().getCode()));
//		taskRecord.setS2oData(JSONWriter.toJsonStringSafe(deanchorResult.getData()));
//
////		taskRecord.update();
//
//		// and set task step as 3
//		taskRecord.setStep(3);
//		taskRecord.setS3iTxdata(txXdr);
//		taskRecord.update();
//
//		// request stellar network submit tx
//		APIResult<TxSubmitResult> stellarTxResult;
//		try {
//			stellarTxResult = stellarNetworkService.submitTx(taskID, txHash, txXdr);
//		} catch(TransactionHashNotMatchException ex) {
//			// update task record
//			logger.error("{} step 3 failed. : {}", ex);
//			taskRecord.setS2oSuccessFlag(false);
//			taskRecord.setS2oCode(Integer.toString(ex.getCode()));
//			taskRecord.setS2oData(JSONWriter.toJsonStringSafe(ex));
//			taskRecord.setFinishFlag(true);
//			taskRecord.update();
//
//			throw ex;
//		}
//
//		if(!stellarTxResult.isSuccess()) {
//			// update task record
//			logger.error("{} step 3 failed. : {}", dexTaskId, stellarTxResult.toString());
//			taskRecord.setS2oSuccessFlag(false);
//			taskRecord.setS2oCode(stellarTxResult.getErrorCode());
//			taskRecord.setS2oData(JSONWriter.toJsonStringSafe(stellarTxResult.getData()));
//			taskRecord.setFinishFlag(true);
//			taskRecord.update();
//
//			throw new APIErrorException(stellarTxResult);
//		}
//
//		// update task record
//		logger.debug("{} step 3 success.", dexTaskId);
//		taskRecord.setS3oSuccessFlag(true);
//		taskRecord.setS3oData(JSONWriter.toJsonStringSafe(stellarTxResult.getData()));
//		taskRecord.setFinishFlag(true);
//		taskRecord.update();
//
//		return new DeanchorSubmitResult(deanchorResult.getData(), stellarTxResult.getData());
//	}
}
