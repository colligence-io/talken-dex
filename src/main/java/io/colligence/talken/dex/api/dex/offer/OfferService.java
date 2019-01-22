package io.colligence.talken.dex.api.dex.offer;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexSettings;
import io.colligence.talken.dex.api.DexTaskId;
import io.colligence.talken.dex.api.dex.TxFeeService;
import io.colligence.talken.dex.api.dex.TxInformation;
import io.colligence.talken.dex.api.dex.offer.dto.*;
import io.colligence.talken.dex.api.mas.ma.ManagedAccountService;
import io.colligence.talken.dex.exception.APICallException;
import io.colligence.talken.dex.exception.AssetTypeNotFoundException;
import io.colligence.talken.dex.exception.TransactionHashNotMatchException;
import io.colligence.talken.dex.service.integration.stellar.StellarNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;

import java.io.IOException;

@Service
@Scope("singleton")
public class OfferService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(OfferService.class);

	@Autowired
	private DexSettings dexSettings;

	@Autowired
	private TxFeeService txFeeService;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private ManagedAccountService maService;

	public CreateOfferResult buildCreateOfferTx(String sourceAccountID, String sellAssetCode, double sellAssetAmount, String buyAssetCode, double sellAssetPrice) throws AssetTypeNotFoundException, APICallException {
		try {
			String taskID = DexTaskId.generate(DexTaskId.Type.OFFER_CREATE).toString();
			// TODO : unique check, insert into db

			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(sourceAccountID);
			KeyPair feeDestination = maService.getOfferFeeHolderAccount(sellAssetCode);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source);

			// calculate fee
			double feeAmount = txFeeService.calculateOfferFee(sellAssetCode, sellAssetAmount);
			double actualSellAmount = sellAssetAmount - feeAmount;

			// get assetType
			AssetTypeCreditAlphaNum4 sellAssetType = maService.getAssetType(sellAssetCode);
			AssetTypeCreditAlphaNum4 buyAssetType = maService.getAssetType(buyAssetCode);

			// build fee operation
			PaymentOperation feePayOperation = new PaymentOperation
					.Builder(feeDestination, sellAssetType, Double.toString(feeAmount))
					.build();

			// build manage offer operation
			ManageOfferOperation offerOperation = new ManageOfferOperation
					.Builder(sellAssetType, buyAssetType, Double.toString(actualSellAmount), Double.toString(sellAssetPrice)).setOfferId(0)
					.build();

			// build tx
			Transaction tx = new Transaction.Builder(sourceAccount)
					.addOperation(feePayOperation)
					.addOperation(offerOperation)
					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
					.build();

			// TODO : insert into taskDB

			return new CreateOfferResult(taskID, TxInformation.buildTxInformation(tx));
		} catch(IOException ioex) {
			throw new APICallException(ioex, "Stellar");
		}
	}

	public CreateOfferSubmitResult submitCreateOfferTx(String taskID, String txHash, String txXdr) throws TransactionHashNotMatchException, APICallException {
		// TODO : update taskDB

		// build refund tx and send it to MAS

		return new CreateOfferSubmitResult(stellarNetworkService.submitTx(taskID, txHash, txXdr));
	}

	public CreatePassiveOfferResult buildCreatePassiveOfferTx(String sourceAccountID, String sellAssetCode, double sellAssetAmount, String buyAssetCode, double sellAssetPrice) throws AssetTypeNotFoundException, APICallException {
		try {
			String taskID = DexTaskId.generate(DexTaskId.Type.OFFER_CREATEPASSIVE).toString();
			// TODO : unique check, insert into db

			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(sourceAccountID);
			KeyPair feeDestination = maService.getOfferFeeHolderAccount(sellAssetCode);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source);

			// calculate fee
			double feeAmount = txFeeService.calculateOfferFee(sellAssetCode, sellAssetAmount);
			double actualSellAmount = sellAssetAmount - feeAmount;

			// get assetType
			AssetTypeCreditAlphaNum4 sellAssetType = maService.getAssetType(sellAssetCode);
			AssetTypeCreditAlphaNum4 buyAssetType = maService.getAssetType(buyAssetCode);

			// build fee operation
			PaymentOperation feePayOperation = new PaymentOperation
					.Builder(feeDestination, sellAssetType, Double.toString(feeAmount))
					.build();

			// build passiveoffer operation
			CreatePassiveOfferOperation offerOperation = new CreatePassiveOfferOperation
					.Builder(sellAssetType, buyAssetType, Double.toString(actualSellAmount), Double.toString(sellAssetPrice))
					.build();

			// build tx
			Transaction tx = new Transaction.Builder(sourceAccount)
					.addOperation(feePayOperation)
					.addOperation(offerOperation)
					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
					.build();

			// TODO : insert into taskDB

			return new CreatePassiveOfferResult(taskID, TxInformation.buildTxInformation(tx));
		} catch(IOException ioex) {
			throw new APICallException(ioex, "Stellar");
		}
	}

	public CreatePassiveOfferSubmitResult submitCreatePassiveOfferTx(String taskID, String txHash, String txXdr) throws APICallException, TransactionHashNotMatchException {
		// TODO : update taskDB

		// build refund tx and send it to MAS

		return new CreatePassiveOfferSubmitResult(stellarNetworkService.submitTx(taskID, txHash, txXdr));
	}

	public DeleteOfferResult buildDeleteOfferTx(long offerId, String sourceAccountID, String sellAssetCode, String buyAssetCode, double sellAssetPrice) throws AssetTypeNotFoundException, APICallException {
		try {
			String taskID = DexTaskId.generate(DexTaskId.Type.OFFER_DELETE).toString();
			// TODO : unique check, insert into db

			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(sourceAccountID);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source);

			// get assetType
			AssetTypeCreditAlphaNum4 sellAssetType = maService.getAssetType(sellAssetCode);
			AssetTypeCreditAlphaNum4 buyAssetType = maService.getAssetType(buyAssetCode);


			// build manage offer operation
			ManageOfferOperation deleteOfferOperation = new ManageOfferOperation
					.Builder(sellAssetType, buyAssetType, Double.toString(0), Double.toString(sellAssetPrice))
					.setOfferId(offerId)
					.build();

			// build tx
			Transaction tx = new Transaction
					.Builder(sourceAccount)
					.addOperation(deleteOfferOperation)
					.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
					.build();

			// TODO : insert task into db

			return new DeleteOfferResult(taskID, TxInformation.buildTxInformation(tx));
		} catch(IOException ioex) {
			throw new APICallException(ioex, "Stellar");
		}
	}

	public DeleteOfferSubmitResult submitDeleteOfferTx(String taskID, String txHash, String txXdr) throws TransactionHashNotMatchException, APICallException {
		// TODO : update taskDB

		// build refund tx and send it to MAS

		return new DeleteOfferSubmitResult(stellarNetworkService.submitTx(taskID, txHash, txXdr));
	}
}
