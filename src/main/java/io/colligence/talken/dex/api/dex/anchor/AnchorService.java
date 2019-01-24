package io.colligence.talken.dex.api.dex.anchor;


import io.colligence.talken.common.persistence.jooq.tables.records.DexAnchorTaskRecord;
import io.colligence.talken.common.util.JSONWriter;
import io.colligence.talken.common.util.PrefixedLogger;
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

	public AnchorResult buildAnchorRequestInformation(long userId, String privateWalletAddress, String tradeWalletAddress, String assetCode, Double amount) throws AssetTypeNotFoundException, APIErrorException, APICallException, TaskIntegrityCheckFailedException {
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
		req.setMemo("");

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

			logger.debug("{} step 1 failed. : {}", dexTaskId, error.toString());
			taskRecord.setS1oSuccessFlag(false);
			taskRecord.setS1oCode(error.getCode());
			taskRecord.setS1oData(JSONWriter.toJsonStringSafe(error.getRawResult()));
			taskRecord.setFinishFlag(true);
			taskRecord.update();

			throw new APIErrorException(error);
		}
	}

	public AnchorSubmitResult submitAnchorTransaction(long userId, String taskID, String assetCode, String txData) throws APIErrorException, APICallException, TaskNotFoundException, TaskIntegrityCheckFailedException {

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
			TxtServerResponse response = txTunnelService.requestTxTunnel(assetCode, request);

			logger.debug("{} step 2 success.", dexTaskId);
			taskRecord.setS2oCode(response.getCode());
			taskRecord.setS2oMessage(response.getMessage());
			taskRecord.setS2oTxid(response.getHash());
			taskRecord.setS2oData(response.getPayload());
			taskRecord.setS2oSuccessFlag(true);
			taskRecord.setFinishFlag(true);
			taskRecord.update();

			return new AnchorSubmitResult(response);
		} catch(APIError error) {

			logger.debug("{} step 2 failed. : {}", dexTaskId, error.toString());
			taskRecord.setS1oSuccessFlag(false);
			taskRecord.setS1oCode(error.getCode());
			taskRecord.setS1oData(JSONWriter.toJsonStringSafe(error.getRawResult()));
			taskRecord.setFinishFlag(true);
			taskRecord.update();

			throw new APIErrorException(error);
		}
	}

	public DeanchorResult buildDeanchorRequestInformation(long userId, String privateWalletAddress, String tradeWalletAddress, String assetCode, Double amount, boolean feeByCtx) throws AssetTypeNotFoundException, APICallException {
		try {
			String taskID = DexTaskId.generate(DexTaskId.Type.DEANCHOR).toString();
			// TODO : unique check, insert into db

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

			// TODO : insert into taskDB

			DeanchorResult deanchorResult = new DeanchorResult();
			deanchorResult.setTaskID(taskID);
			deanchorResult.setTxInformation(TxInformation.buildTxInformation(tx));
			return deanchorResult;
		} catch(IOException ioex) {
			throw new APICallException(ioex, "Stellar");
		}
	}

	public DeanchorSubmitResult submitDeanchorTransaction(long userId, String taskID, String txHash, String txXdr) throws TransactionHashNotMatchException, APICallException, APIErrorException {
		// TODO : check taskId integrity


		TxSubmitResult txSubmitResult = stellarNetworkService.submitTx(taskID, txHash, txXdr);
		AncServerDeanchorResponse deanchorResponse;

		if(txSubmitResult.isSuccess()) {
			// TODO : request deanchor

			String assetCode = "";
			String privateAddress = "";
			String tradeAddress = "";
			String assetBaseAddress = "";
			Float amount = 0f;

			AncServerDeanchorRequest request = new AncServerDeanchorRequest();
			request.setTaskId(taskID);
			request.setSymbol(assetCode);
			request.setHash(txHash);
			request.setFrom(tradeAddress);
			request.setTo(assetBaseAddress);
			request.setAddress(privateAddress);
			request.setValue(amount);

			try {
				deanchorResponse = anchorServerService.requestDeanchor(request);
			} catch(APIError apiError) {
				// TODO : log error

				throw new APIErrorException(apiError);
			}

		} else {
			// TODO : log error
		}

		DeanchorSubmitResult result = new DeanchorSubmitResult(txSubmitResult);
		return result;
	}
}
