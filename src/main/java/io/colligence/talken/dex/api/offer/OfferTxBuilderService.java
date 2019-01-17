package io.colligence.talken.dex.api.offer;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexSettings;
import io.colligence.talken.dex.api.TxInformation;
import io.colligence.talken.dex.service.AssetTypeService;
import io.colligence.talken.dex.service.StellarNetworkService;
import io.colligence.talken.dex.service.TxFeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.xdr.DecoratedSignature;
import org.stellar.sdk.xdr.TransactionEnvelope;
import org.stellar.sdk.xdr.XdrDataOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Service
@Scope("singleton")
public class OfferTxBuilderService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(OfferTxBuilderService.class);

	@Autowired
	private DexSettings dexSettings;

	@Autowired
	private AssetTypeService assetTypeService;

	@Autowired
	private TxFeeService txFeeService;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	public TxInformation buildOfferTx(String sourceAccountID, String sellAssetCode, double sellAssetAmount, String buyAssetCode, double sellAssetPrice) throws DexException, IOException {
		// pick horizon server
		Server server = stellarNetworkService.pickServer();

		// prepare accounts
		KeyPair source = KeyPair.fromAccountId(sourceAccountID);
		KeyPair feeDestination = txFeeService.getOfferFeeHolderAccount();

		// load up-to-date information on source account.
		AccountResponse sourceAccount = server.accounts().account(source);

		// calculate fee
		double feeAmount = txFeeService.calculateOfferFee(sellAssetCode, sellAssetAmount);
		double actualSellAmount = sellAssetAmount - feeAmount;

		// get assetType
		AssetTypeCreditAlphaNum4 sellAssetType = assetTypeService.getAssetType(sellAssetCode);
		AssetTypeCreditAlphaNum4 buyAssetType = assetTypeService.getAssetType(buyAssetCode);

		// build fee operation
		PaymentOperation feePayOperation = new PaymentOperation.Builder(feeDestination, sellAssetType, Double.toString(feeAmount)).build();

		// build passiveoffer operation
		CreatePassiveOfferOperation passiveOfferOperation = new CreatePassiveOfferOperation.Builder(sellAssetType, buyAssetType, Double.toString(actualSellAmount), Double.toString(sellAssetPrice)).build();

		// build tx
		Transaction tx = new Transaction.Builder(sourceAccount)
				.addOperation(feePayOperation)
				.addOperation(passiveOfferOperation)
				.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
				.build();

		// encode tx to envelope
		TransactionEnvelope xdr = new TransactionEnvelope();
		xdr.setTx(tx.toXdr());
		xdr.setSignatures(new DecoratedSignature[0]);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		XdrDataOutputStream xdos = new XdrDataOutputStream(baos);
		TransactionEnvelope.encode(xdos, xdr);
		byte[] txEnvelope = baos.toByteArray();

		// encode to base64
		TxInformation txInfo = new TxInformation();
		txInfo.setNetworkPhrase(Network.current().getNetworkPassphrase());
		txInfo.setSequence(tx.getSequenceNumber());
		txInfo.setHash(ByteArrayUtil.toHexString(tx.hash()));
		txInfo.setEnvelopeXdr(Base64.getEncoder().encodeToString(txEnvelope));

		return txInfo;
	}
}
