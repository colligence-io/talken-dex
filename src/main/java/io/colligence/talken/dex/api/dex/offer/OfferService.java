package io.colligence.talken.dex.api.dex.offer;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.api.DexTaskId;
import io.colligence.talken.dex.api.dex.TxFeeService;
import io.colligence.talken.dex.api.dex.TxInformation;
import io.colligence.talken.dex.api.dex.TxSubmitResult;
import io.colligence.talken.dex.api.dex.offer.dto.*;
import io.colligence.talken.dex.api.mas.ma.ManagedAccountService;
import io.colligence.talken.dex.exception.APIErrorException;
import io.colligence.talken.dex.exception.AssetTypeNotFoundException;
import io.colligence.talken.dex.exception.StellarException;
import io.colligence.talken.dex.exception.TransactionHashNotMatchException;
import io.colligence.talken.dex.service.integration.APIResult;
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
	private TxFeeService txFeeService;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private ManagedAccountService maService;

	public CreateOfferResult buildCreateOfferTx(long userId, String sourceAccountID, String sellAssetCode, double sellAssetAmount, String buyAssetCode, double sellAssetPrice, boolean feeByCtx) throws AssetTypeNotFoundException, StellarException {
		try {
			String taskID = DexTaskId.generate(DexTaskId.Type.OFFER_CREATE).toString();
			// TODO : unique check, insert into db

			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(sourceAccountID);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source);

			// calculate fee
			TxFeeService.Fee fee = txFeeService.calculateOfferFee(sellAssetCode, sellAssetAmount, feeByCtx);

			// get assetType
			AssetTypeCreditAlphaNum4 buyAssetType = maService.getAssetType(buyAssetCode);

			Transaction.Builder txBuilder = new Transaction.Builder(sourceAccount).setTimeout(Transaction.Builder.TIMEOUT_INFINITE);

			if(fee.getFeeAmount() > 0) {
				// build fee operation
				txBuilder.addOperation(
						new PaymentOperation
								.Builder(fee.getFeeCollectorAccount(), fee.getFeeAssetType(), Double.toString(fee.getFeeAmount()))
								.build()
				);
			}

			// build manage offer operation
			txBuilder.addOperation(
					new ManageOfferOperation
							.Builder(fee.getSellAssetType(), buyAssetType, Double.toString(fee.getSellAmount()), Double.toString(sellAssetPrice)).setOfferId(0)
							.build()
			);

			// build tx
			Transaction tx = txBuilder.build();

			// TODO : insert into taskDB

			return new CreateOfferResult(taskID, TxInformation.buildTxInformation(tx));
		} catch(IOException ioex) {
			throw new StellarException(ioex);
		}
	}

	public CreateOfferSubmitResult submitCreateOfferTx(long userId, String taskID, String txHash, String txXdr) throws TransactionHashNotMatchException, APIErrorException {
		// TODO : update taskDB

		// build refund tx and send it to MAS

		APIResult<TxSubmitResult> stellarTxResult = stellarNetworkService.submitTx(taskID, txHash, txXdr);
		if(!stellarTxResult.isSuccess()) {
			throw new APIErrorException(stellarTxResult);
		}

		return new CreateOfferSubmitResult(stellarTxResult.getData());
	}

	public CreatePassiveOfferResult buildCreatePassiveOfferTx(long userId, String sourceAccountID, String sellAssetCode, double sellAssetAmount, String buyAssetCode, double sellAssetPrice, boolean feeByCtx) throws AssetTypeNotFoundException, StellarException {
		try {
			String taskID = DexTaskId.generate(DexTaskId.Type.OFFER_CREATEPASSIVE).toString();
			// TODO : unique check, insert into db

			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(sourceAccountID);
//			KeyPair feeDestination = maService.getOfferFeeHolderAccount(sellAssetCode);

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source);

			// calculate fee
			TxFeeService.Fee fee = txFeeService.calculateOfferFee(sellAssetCode, sellAssetAmount, feeByCtx);

			// get assetType
			AssetTypeCreditAlphaNum4 buyAssetType = maService.getAssetType(buyAssetCode);

			Transaction.Builder txBuilder = new Transaction.Builder(sourceAccount).setTimeout(Transaction.Builder.TIMEOUT_INFINITE);

			if(fee.getFeeAmount() > 0) {
				// build fee operation
				txBuilder.addOperation(
						new PaymentOperation
								.Builder(fee.getFeeCollectorAccount(), fee.getFeeAssetType(), Double.toString(fee.getFeeAmount()))
								.build()
				);
			}

			// build manage offer operation
			txBuilder.addOperation(
					new CreatePassiveOfferOperation
							.Builder(fee.getSellAssetType(), buyAssetType, Double.toString(fee.getSellAmount()), Double.toString(sellAssetPrice))
							.build()
			);

			// build tx
			Transaction tx = txBuilder.build();

			// TODO : insert into taskDB

			return new CreatePassiveOfferResult(taskID, TxInformation.buildTxInformation(tx));
		} catch(IOException ioex) {
			throw new StellarException(ioex);
		}
	}

	public CreatePassiveOfferSubmitResult submitCreatePassiveOfferTx(long userId, String taskID, String txHash, String txXdr) throws TransactionHashNotMatchException, APIErrorException {
		// TODO : update taskDB

		// build refund tx and send it to MAS

		APIResult<TxSubmitResult> stellarTxResult = stellarNetworkService.submitTx(taskID, txHash, txXdr);
		if(!stellarTxResult.isSuccess()) {
			throw new APIErrorException(stellarTxResult);
		}

		return new CreatePassiveOfferSubmitResult(stellarTxResult.getData());
	}

	public DeleteOfferResult buildDeleteOfferTx(long userId, long offerId, String sourceAccountID, String sellAssetCode, String buyAssetCode, double sellAssetPrice) throws AssetTypeNotFoundException, StellarException {
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
			throw new StellarException(ioex);
		}
	}

	public DeleteOfferSubmitResult submitDeleteOfferTx(long userId, String taskID, String txHash, String txXdr) throws TransactionHashNotMatchException, APIErrorException {
		// TODO : update taskDB

		// build refund tx and send it to MAS

		APIResult<TxSubmitResult> stellarTxResult = stellarNetworkService.submitTx(taskID, txHash, txXdr);
		if(!stellarTxResult.isSuccess()) {
			throw new APIErrorException(stellarTxResult);
		}

		return new DeleteOfferSubmitResult(stellarTxResult.getData());
	}
}
