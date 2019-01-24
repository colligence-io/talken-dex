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
import io.colligence.talken.dex.api.dex.anchor.dto.AnchorResult;
import io.colligence.talken.dex.api.dex.anchor.dto.AnchorSubmitResult;
import io.colligence.talken.dex.api.dex.anchor.dto.DeanchorResult;
import io.colligence.talken.dex.api.dex.anchor.dto.DeanchorSubmitResult;
import io.colligence.talken.dex.api.mas.ma.ManagedAccountService;
import io.colligence.talken.dex.exception.*;
import io.colligence.talken.dex.service.integration.APIError;
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

	public AnchorResult buildAnchorRequestInformation(long userId, String privateWalletAddress, String tradeWalletAddress, String assetCode, Double amount) throws AssetTypeNotFoundException, APIErrorException, APICallException {
		String assetHolderAddress = maService.getHolderAccountAddress(assetCode);

		DexTaskId dexTaskId = DexTaskId.generate(DexTaskId.Type.ANCHOR);

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

		try {
			AncServerAnchorResponse response = anchorServerService.requestAnchor(req);

			logger.debug("{} step 1 success.", dexTaskId);
			taskRecord.setS1oSuccessFlag(true);
			taskRecord.setS1oCode(String.valueOf(response.getCode()));
			taskRecord.setS1oData(JSONWriter.toJsonStringSafe(response));
			taskRecord.update();

			AnchorResult result = new AnchorResult();
			result.setTaskId(dexTaskId.getId());
			result.setHolderAccountAddress(response.getData().getAddress());
			return result;
		} catch(APIError error) {

			logger.error("{} step 1 failed. : {}", dexTaskId, error.toString());
			taskRecord.setS1oSuccessFlag(false);
			taskRecord.setS1oCode(error.getCode());
			taskRecord.setS1oData(JSONWriter.toJsonStringSafe(error.getRawResult()));
			taskRecord.setFinishFlag(true);
			taskRecord.update();

			throw new APIErrorException(error);
		}
	}

	public AnchorSubmitResult submitAnchorTransaction(long userId, String taskID, String assetCode, String txData) throws APIErrorException, APICallException, TaskNotFoundException, TaskIntegrityCheckFailedException, AssetTypeNotFoundException {

		DexTaskId dexTaskId = DexTaskId.fromId(taskID);
		DexAnchorTaskRecord taskRecord = dslContext.selectFrom(DEX_ANCHOR_TASK)
				.where(DEX_ANCHOR_TASK.TASKID.eq(taskID))
				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskID));

		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskID);
		if(!taskRecord.getS1iAssetcode().equalsIgnoreCase(assetCode)) throw new TaskIntegrityCheckFailedException(taskID);

		taskRecord.setStep(2);
		taskRecord.setS2iTxdata(txData);
		taskRecord.update();

		TxtServerRequest request = new TxtServerRequest();
		request.setServiceId(dexSettings.getServer().getTxtServerId());
		request.setTaskId(taskID);
		request.setSignatures(txData);

		try {
			TxtServerResponse txtResponse = txTunnelService.requestTxTunnel(maService.getAssetPlatform(assetCode), request);

			logger.debug("{} step 2 success.", dexTaskId);
			taskRecord.setS2oCode(txtResponse.getCode());
			taskRecord.setS2oMessage(txtResponse.getMessage());
			taskRecord.setS2oTxid(txtResponse.getHash());
			taskRecord.setS2oData(txtResponse.getPayload());
			taskRecord.setS2oSuccessFlag(true);
			taskRecord.setFinishFlag(true);
			taskRecord.update();

			AnchorSubmitResult result = new AnchorSubmitResult();
			result.setTxtServerResponse(txtResponse);
			return result;
		} catch(APIError error) {

			logger.error("{} step 2 failed. : {}", dexTaskId, error.toString());
			taskRecord.setS2oSuccessFlag(false);
			taskRecord.setS2oCode(error.getCode());
			taskRecord.setS2oData(JSONWriter.toJsonStringSafe(error.getRawResult()));
			taskRecord.setFinishFlag(true);
			taskRecord.update();

			throw new APIErrorException(error);
		}
	}

	public DeanchorResult buildDeanchorRequestInformation(long userId, String privateWalletAddress, String tradeWalletAddress, String assetCode, Double amount, Boolean feeByCtx) throws AssetTypeNotFoundException, APICallException {
		if(feeByCtx == null) feeByCtx = false;

		DexTaskId dexTaskId = DexTaskId.generate(DexTaskId.Type.DEANCHOR);

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

			logger.debug("{} step 1 failed. : {}", ioex.getMessage());
			taskRecord.setS1oSuccessFlag(false);
			taskRecord.setS1oData("IOException: " + ioex.getMessage());
			taskRecord.setFinishFlag(true);

			throw new APICallException(ioex, "Stellar");
		}
	}

	public DeanchorSubmitResult submitDeanchorTransaction(long userId, String taskID, String txHash, String txXdr) throws TaskIntegrityCheckFailedException, TaskNotFoundException, APIErrorException, APICallException, AssetTypeNotFoundException {
		DexTaskId dexTaskId = DexTaskId.fromId(taskID);
		DexDeanchorTaskRecord taskRecord = dslContext.selectFrom(DEX_DEANCHOR_TASK)
				.where(DEX_ANCHOR_TASK.TASKID.eq(taskID))
				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskID));

		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskID);
		if(!taskRecord.getS1oTxhash().equalsIgnoreCase(txHash)) throw new TaskIntegrityCheckFailedException(taskID);

		taskRecord.setS2iTxdata(txXdr);
		taskRecord.setStep(2);
		taskRecord.update();

		TxtServerRequest txtRequest = new TxtServerRequest();
		txtRequest.setServiceId(dexSettings.getServer().getTxtServerId());
		txtRequest.setTaskId(taskID);
		txtRequest.setSignatures(txXdr);

		TxtServerResponse txtResponse;
		try {
			txtResponse = txTunnelService.requestTxTunnel(maService.getAssetPlatform(taskRecord.getS1iAssetcode()), txtRequest);

			logger.debug("{} step 2 success.", dexTaskId);
			taskRecord.setS2oCode(txtResponse.getCode());
			taskRecord.setS2oMessage(txtResponse.getMessage());
			taskRecord.setS2oTxid(txtResponse.getHash());
			taskRecord.setS2oData(txtResponse.getPayload());
			taskRecord.setS2oSuccessFlag(true);

			taskRecord.setStep(3);

			taskRecord.update();

		} catch(APIError error) {

			logger.error("{} step 2 failed. : {}", dexTaskId, error.toString());
			taskRecord.setS2oSuccessFlag(false);
			taskRecord.setS2oCode(error.getCode());
			taskRecord.setS2oData(JSONWriter.toJsonStringSafe(error.getRawResult()));
			taskRecord.setFinishFlag(true);
			taskRecord.update();

			throw new APIErrorException(error);
		} catch(APICallException ex) {

			logger.error("{} step 2 failed. : {}", dexTaskId, ex.getMessage());
			taskRecord.setS2oSuccessFlag(false);
			taskRecord.setS2oCode("APICallException");
			taskRecord.setS2oData(ex.getMessage());
			taskRecord.setFinishFlag(true);
			taskRecord.update();

			throw ex;
		}

		AncServerDeanchorRequest ancRequest = new AncServerDeanchorRequest();
		ancRequest.setTaskId(taskID);
		ancRequest.setSymbol(taskRecord.getS1iAssetcode());
		ancRequest.setHash(txHash);
		ancRequest.setFrom(taskRecord.getS1iTradeaddr());
		ancRequest.setTo(taskRecord.getS1jBaseaccount());
		ancRequest.setAddress(taskRecord.getS1iPrivateaddr());
		ancRequest.setValue(taskRecord.getS1jDeanchoramount().floatValue());
		ancRequest.setMemo(UTCUtil.getNow().toString());

		AncServerDeanchorResponse ancResponse;
		try {
			ancResponse = anchorServerService.requestDeanchor(ancRequest);

			taskRecord.setS3oSuccessFlag(true);
			taskRecord.setS3oCode(Integer.toString(ancResponse.getCode()));
			taskRecord.setS3oMessage(ancResponse.getDescription());
			taskRecord.setFinishFlag(true);
			taskRecord.update();

		} catch(APIError error) {

			logger.error("{} step 3 failed. : {}", dexTaskId, error.toString());
			taskRecord.setS3oSuccessFlag(false);
			taskRecord.setS3oCode(error.getCode());
			taskRecord.setS3oData(JSONWriter.toJsonStringSafe(error.getRawResult()));
			taskRecord.setFinishFlag(true);
			taskRecord.update();

			throw new APIErrorException(error);
		} catch(APICallException ex) {

			logger.error("{} step 3 failed. : {}", dexTaskId, ex.getMessage());
			taskRecord.setS3oSuccessFlag(false);
			taskRecord.setS3oCode("APICallException");
			taskRecord.setS3oData(ex.getMessage());
			taskRecord.setFinishFlag(true);
			taskRecord.update();

			throw ex;
		}

		DeanchorSubmitResult result = new DeanchorSubmitResult();
		result.setTxtServerResponse(txtResponse);
		result.setDeanchorResponse(ancResponse);
		return result;
	}
}
