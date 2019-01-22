package io.colligence.talken.dex.api.dex.anchor;

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
import io.colligence.talken.dex.exception.APICallException;
import io.colligence.talken.dex.exception.APIErrorException;
import io.colligence.talken.dex.exception.AssetTypeNotFoundException;
import io.colligence.talken.dex.exception.TransactionHashNotMatchException;
import io.colligence.talken.dex.service.integration.APIError;
import io.colligence.talken.dex.service.integration.anchor.*;
import io.colligence.talken.dex.service.integration.stellar.StellarNetworkService;
import io.colligence.talken.dex.service.integration.txTunnel.TransactionTunnelService;
import io.colligence.talken.dex.service.integration.txTunnel.TxtServerRequest;
import io.colligence.talken.dex.service.integration.txTunnel.TxtServerResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;

import java.io.IOException;

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

	public AnchorResult buildAnchorRequestInformation(String privateWalletAddress, String tradeWalletAddress, String assetCode, Double amount) throws AssetTypeNotFoundException, APIErrorException, APICallException {
		String assetHolderAddress = maService.getHolderAccountAddress(assetCode);

		String taskID = DexTaskId.generate(DexTaskId.Type.ANCHOR).toString();
		// TODO : unique check, insert into db

		AncServerAnchorRequest req = new AncServerAnchorRequest();
		req.setTaskId(taskID);
		req.setUid("0"); // TODO : use actual uid
		req.setFrom(privateWalletAddress);
		req.setTo(assetHolderAddress);
		req.setStellar(tradeWalletAddress);
		req.setSymbol(assetCode);
		req.setValue(amount.floatValue());
		req.setMemo("");

		try {
			AncServerAnchorResponse asar = anchorServerService.requestAnchor(req);

			AnchorResult result = new AnchorResult();
			result.setTaskId(taskID);
			result.setHolderAccountAddress(asar.getData().getAddress());
			return result;
		} catch(APIError error) {
			// TODO : log error

			throw new APIErrorException(error);
		}
	}

	public AnchorSubmitResult submitAnchorTransaction(String assetCode, String taskID, String txData) throws APIErrorException, APICallException {
		TxtServerRequest request = new TxtServerRequest();
		request.setServiceId(dexSettings.getServer().getTxtServerId());
		request.setTaskId(taskID);
		request.setTxData(txData);

		try {
			TxtServerResponse response = txTunnelService.requestTxTunnel(assetCode, request);
			// TODO : update taskDB

			return new AnchorSubmitResult(response);
		} catch(APIError error) {
			// TODO : update taskDB

			throw new APIErrorException(error);
		}
	}

	public DeanchorResult buildDeanchorRequestInformation(String privateWalletAddress, String tradeWalletAddress, String assetCode, Double amount, boolean feeByCtx) throws AssetTypeNotFoundException, APICallException {
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

			return new DeanchorResult(taskID, TxInformation.buildTxInformation(tx));
		} catch(IOException ioex) {
			throw new APICallException(ioex, "Stellar");
		}
	}

	public DeanchorSubmitResult submitDeanchorTransaction(String taskID, String txHash, String txXdr) throws TransactionHashNotMatchException, APICallException, APIErrorException {
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
