package io.colligence.talken.dex.api.dex.anchor;


import io.colligence.talken.common.persistence.jooq.tables.records.DexAnchorTaskRecord;
import io.colligence.talken.common.persistence.jooq.tables.records.DexDeanchorTaskRecord;
import io.colligence.talken.common.util.JSONWriter;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.common.util.UTCUtil;
import io.colligence.talken.dex.DexSettings;
import io.colligence.talken.dex.api.DexTaskId;
import io.colligence.talken.dex.api.dex.TxFeeService;
import io.colligence.talken.dex.api.dex.TxInformation;
import io.colligence.talken.dex.api.dex.TxSubmitResult;
import io.colligence.talken.dex.api.dex.anchor.dto.AnchorResult;
import io.colligence.talken.dex.api.dex.anchor.dto.AnchorSubmitResult;
import io.colligence.talken.dex.api.dex.anchor.dto.DeanchorResult;
import io.colligence.talken.dex.api.dex.anchor.dto.DeanchorSubmitResult;
import io.colligence.talken.dex.api.mas.ma.ManagedAccountService;
import io.colligence.talken.dex.exception.*;
import io.colligence.talken.dex.service.integration.APIResult;
import io.colligence.talken.dex.service.integration.anchor.*;
import io.colligence.talken.dex.service.integration.stellar.StellarNetworkService;
import io.colligence.talken.dex.service.integration.txTunnel.TransactionTunnelService;
import io.colligence.talken.dex.service.integration.txTunnel.TxtServerRequest;
import io.colligence.talken.dex.service.integration.txTunnel.TxtServerResponse;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;

import java.io.IOException;

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
	private TransactionTunnelService txTunnelService;

	@Autowired
	private AnchorServerService anchorServerService;

	@Autowired
	private ManagedAccountService maService;

	@Autowired
	private TxFeeService txFeeService;

	@Autowired
	private DSLContext dslContext;

	public AnchorResult buildAnchorRequestInformation(long userId, String privateWalletAddress, String tradeWalletAddress, String assetCode, Double amount) throws AssetTypeNotFoundException, APIErrorException, ActiveAssetHolderAccountNotFoundException {
		String assetHolderAddress = maService.getActiveHolderAccountAddress(assetCode);

		DexTaskId dexTaskId = DexTaskId.generate(DexTaskId.Type.ANCHOR);

		// create task record
		DexAnchorTaskRecord taskRecord = new DexAnchorTaskRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);
		taskRecord.setStep(1);
		taskRecord.setFinishFlag(false);

		taskRecord.setS1iPrivateaddr(privateWalletAddress);
		taskRecord.setS1iTradeaddr(tradeWalletAddress);
		taskRecord.setS1iHolderaddr(assetHolderAddress);
		taskRecord.setS1iAssetcode(assetCode);
		taskRecord.setS1iAmount(amount);

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
			logger.error("{} step 1 failed. : {}", dexTaskId, anchorResult.toString());
			taskRecord.setS1oSuccessFlag(false);
			taskRecord.setS1oCode(anchorResult.getErrorCode());
			taskRecord.setS1oData(JSONWriter.toJsonStringSafe(anchorResult.getData()));
			taskRecord.setFinishFlag(true);
			taskRecord.update();

			throw new APIErrorException(anchorResult);
		}

		// update task record
		logger.debug("{} step 1 success.", dexTaskId);
		taskRecord.setS1oSuccessFlag(true);
		taskRecord.setS1oCode(String.valueOf(anchorResult.getData().getCode()));
		taskRecord.setS1oData(JSONWriter.toJsonStringSafe(anchorResult.getData()));
		taskRecord.update();

		AnchorResult result = new AnchorResult();
		result.setTaskId(dexTaskId.getId());
		result.setHolderAccountAddress(assetHolderAddress);
		return result;
	}

	public AnchorSubmitResult submitAnchorTransaction(long userId, String taskID, String assetCode, String txData) throws APIErrorException, TaskNotFoundException, TaskIntegrityCheckFailedException, AssetTypeNotFoundException {

		DexTaskId dexTaskId = DexTaskId.fromId(taskID);
		DexAnchorTaskRecord taskRecord = dslContext.selectFrom(DEX_ANCHOR_TASK)
				.where(DEX_ANCHOR_TASK.TASKID.eq(taskID))
				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskID));

		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskID);
		if(!taskRecord.getS1iAssetcode().equalsIgnoreCase(assetCode)) throw new TaskIntegrityCheckFailedException(taskID);

		// update task record as step 2
		taskRecord.setStep(2);
		taskRecord.setS2iTxdata(txData);
		taskRecord.update();

		TxtServerRequest request = new TxtServerRequest();
		request.setServiceId(dexSettings.getServer().getTxtServerId());
		request.setTaskId(taskID);
		request.setSignatures(txData);

		APIResult<TxtServerResponse> txtResult = txTunnelService.requestTxTunnel(maService.getAssetPlatform(assetCode), request);
		if(!txtResult.isSuccess()) {
			// update task record
			logger.error("{} step 2 failed. : {}", dexTaskId, txtResult.toString());
			taskRecord.setS2oSuccessFlag(false);
			taskRecord.setS2oCode(txtResult.getErrorCode());
			taskRecord.setS2oData(JSONWriter.toJsonStringSafe(txtResult.getData()));
			taskRecord.setFinishFlag(true);
			taskRecord.update();

			throw new APIErrorException(txtResult);
		}

		// update task record
		logger.debug("{} step 2 success.", dexTaskId);
		taskRecord.setS2oCode(txtResult.getData().getCode());
		taskRecord.setS2oMessage(txtResult.getData().getMessage());
		taskRecord.setS2oTxid(txtResult.getData().getHash());
		taskRecord.setS2oData(txtResult.getData().getPayload());
		taskRecord.setS2oSuccessFlag(true);
		taskRecord.setFinishFlag(true);
		taskRecord.update();

		return new AnchorSubmitResult(txtResult.getData());
	}

	public DeanchorResult buildDeanchorRequestInformation(long userId, String privateWalletAddress, String tradeWalletAddress, String assetCode, Double amount, Boolean feeByCtx) throws AssetTypeNotFoundException, StellarException {
		if(feeByCtx == null) feeByCtx = false;

		DexTaskId dexTaskId = DexTaskId.generate(DexTaskId.Type.DEANCHOR);

		// create task record
		DexDeanchorTaskRecord taskRecord = new DexDeanchorTaskRecord();
		taskRecord.setTaskid(dexTaskId.getId());
		taskRecord.setUserId(userId);
		taskRecord.setStep(1);
		taskRecord.setFinishFlag(false);

		taskRecord.setS1iPrivateaddr(privateWalletAddress);
		taskRecord.setS1iTradeaddr(tradeWalletAddress);
		taskRecord.setS1iAssetcode(assetCode);
		taskRecord.setS1iAmount(amount);
		taskRecord.setS1iFeebyctx(feeByCtx);

		dslContext.attach(taskRecord);
		taskRecord.store();

		logger.debug("{} generated. userId = {}", dexTaskId, userId);

		try {
			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(tradeWalletAddress);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source);

			AssetTypeCreditAlphaNum4 deanchorAssetType = maService.getAssetType(assetCode);
			KeyPair baseAccount = maService.getBaseAccount(assetCode);

			AssetTypeCreditAlphaNum4 feeAssetType;
			KeyPair feeCollectAccount;

			double feeAmount;
			double deanchorAmount;

			Transaction tx;

			if(feeByCtx) {
				// calculate fee
				feeAmount = txFeeService.calculateDeanchorFeeByCtx(assetCode, amount);
				deanchorAmount = amount;

				feeAssetType = maService.getAssetType("CTX");
				feeCollectAccount = maService.getDeanchorFeeHolderAccount("CTX");
			} else {
				// calculate fee
				feeAmount = txFeeService.calculateDeanchorFee(assetCode, amount);
				deanchorAmount = amount - feeAmount;

				feeAssetType = maService.getAssetType(assetCode);
				feeCollectAccount = maService.getDeanchorFeeHolderAccount(assetCode);
			}

			// build fee operation
			PaymentOperation feePayOperation = new PaymentOperation
					.Builder(feeCollectAccount, feeAssetType, Double.toString(feeAmount))
					.build();

			// build deanchor operation
			PaymentOperation deanchorOperation = new PaymentOperation
					.Builder(baseAccount, deanchorAssetType, Double.toString(deanchorAmount))
					.build();

			// build tx
			tx = new Transaction.Builder(sourceAccount)
					.addOperation(feePayOperation)
					.addOperation(deanchorOperation)
					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
					.build();

			TxInformation txInformation = TxInformation.buildTxInformation(tx);

			// update task record
			logger.debug("{} step 1 success.", dexTaskId);
			taskRecord.setS1jBaseaccount(baseAccount.getAccountId());
			taskRecord.setS1jDeanchoramount(deanchorAmount);
			taskRecord.setS1jFeeamount(feeAmount);
			taskRecord.setS1jFeeassettype(feeAssetType.getType());
			taskRecord.setS1jFeecollectaccount(feeCollectAccount.getAccountId());
			taskRecord.setS1oSuccessFlag(true);
			taskRecord.setS1oSequence(txInformation.getSequence());
			taskRecord.setS1oTxhash(txInformation.getHash());
			taskRecord.setS1oData(txInformation.getEnvelopeXdr());
			taskRecord.update();

			DeanchorResult result = new DeanchorResult();
			result.setTaskID(dexTaskId.getId());
			result.setTxInformation(txInformation);
			result.setFeeAssetType(feeAssetType.getType());
			result.setFeeAmount(feeAmount);
			result.setDeanchorAssetType(deanchorAssetType.getType());
			result.setDeanchorAmount(deanchorAmount);
			return result;
		} catch(IOException ioex) {

			// update task record
			logger.debug("{} step 1 failed. : {}", ioex.getMessage());
			taskRecord.setS1oSuccessFlag(false);
			taskRecord.setS1oData("IOException: " + ioex.getMessage());
			taskRecord.setFinishFlag(true);

			throw new StellarException(ioex);
		}
	}

	public DeanchorSubmitResult submitDeanchorTransaction(long userId, String taskID, String txHash, String txXdr) throws TaskIntegrityCheckFailedException, TaskNotFoundException, APIErrorException, TransactionHashNotMatchException {
		DexTaskId dexTaskId = DexTaskId.fromId(taskID);
		DexDeanchorTaskRecord taskRecord = dslContext.selectFrom(DEX_DEANCHOR_TASK)
				.where(DEX_ANCHOR_TASK.TASKID.eq(taskID))
				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskID));

		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskID);
		if(!taskRecord.getS1oTxhash().equalsIgnoreCase(txHash)) throw new TaskIntegrityCheckFailedException(taskID);

		// update task step as 2
		taskRecord.setStep(2);
		taskRecord.update();

		// request deanchor monitor
		AncServerDeanchorRequest ancRequest = new AncServerDeanchorRequest();
		ancRequest.setTaskId(taskID);
		ancRequest.setSymbol(taskRecord.getS1iAssetcode());
		ancRequest.setHash(txHash);
		ancRequest.setFrom(taskRecord.getS1iTradeaddr());
		ancRequest.setTo(taskRecord.getS1jBaseaccount());
		ancRequest.setAddress(taskRecord.getS1iPrivateaddr());
		ancRequest.setValue(taskRecord.getS1jDeanchoramount().floatValue());
		ancRequest.setMemo(UTCUtil.getNow().toString());

		APIResult<AncServerDeanchorResponse> deanchorResult = anchorServerService.requestDeanchor(ancRequest);
		if(!deanchorResult.isSuccess()) {
			logger.debug("{} step 2 failed.", dexTaskId, deanchorResult);

			taskRecord.setS2oSuccessFlag(false);
			taskRecord.setS2oCode(deanchorResult.getErrorCode());
			taskRecord.setS2oData(JSONWriter.toJsonStringSafe(deanchorResult.getData()));
			taskRecord.setFinishFlag(true);

			throw new APIErrorException(deanchorResult);
		}

		// update task record
		logger.debug("{} step 2 success.", dexTaskId);
		taskRecord.setS2oSuccessFlag(true);
		taskRecord.setS2oCode(Integer.toString(deanchorResult.getData().getCode()));
		taskRecord.setS2oData(JSONWriter.toJsonStringSafe(deanchorResult.getData()));

//		taskRecord.update();

		// and set task step as 3
		taskRecord.setStep(3);
		taskRecord.setS3iTxdata(txXdr);
		taskRecord.update();

		// request stellar network submit tx
		APIResult<TxSubmitResult> stellarTxResult;
		try {
			stellarTxResult = stellarNetworkService.submitTx(taskID, txHash, txXdr);
		} catch(TransactionHashNotMatchException ex) {
			// update task record
			logger.error("{} step 3 failed. : {}", ex);
			taskRecord.setS2oSuccessFlag(false);
			taskRecord.setS2oCode(Integer.toString(ex.getCode()));
			taskRecord.setS2oData(JSONWriter.toJsonStringSafe(ex));
			taskRecord.setFinishFlag(true);
			taskRecord.update();

			throw ex;
		}

		if(!stellarTxResult.isSuccess()) {
			// update task record
			logger.error("{} step 3 failed. : {}", dexTaskId, stellarTxResult.toString());
			taskRecord.setS2oSuccessFlag(false);
			taskRecord.setS2oCode(stellarTxResult.getErrorCode());
			taskRecord.setS2oData(JSONWriter.toJsonStringSafe(stellarTxResult.getData()));
			taskRecord.setFinishFlag(true);
			taskRecord.update();

			throw new APIErrorException(stellarTxResult);
		}

		// update task record
		logger.debug("{} step 3 success.", dexTaskId);
		taskRecord.setS3oSuccessFlag(true);
		taskRecord.setS3oData(JSONWriter.toJsonStringSafe(stellarTxResult.getData()));
		taskRecord.setFinishFlag(true);
		taskRecord.update();

		return new DeanchorSubmitResult(deanchorResult.getData(), stellarTxResult.getData());
	}
}
