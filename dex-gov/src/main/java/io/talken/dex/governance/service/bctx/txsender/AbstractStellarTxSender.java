package io.talken.dex.governance.service.bctx.txsender;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import com.google.gson.JsonObject;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.records.BctxLogRecord;
import io.talken.common.util.GSONWriter;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.collection.ObjectPair;
import io.talken.dex.governance.service.bctx.TxSender;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;

public abstract class AbstractStellarTxSender extends TxSender {
	private final PrefixedLogger logger;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	public AbstractStellarTxSender(BlockChainPlatformEnum platform, PrefixedLogger logger) {
		super(platform);
		this.logger = logger;
	}

	protected boolean sendStellarTx(Asset asset, Bctx bctx, BctxLogRecord log) throws Exception {
		// pick horizon server
		Server server = stellarNetworkService.pickServer();

		// prepare accounts
		KeyPair source = KeyPair.fromAccountId(bctx.getAddressFrom());

		// load up-to-date information on source account.
		AccountResponse sourceAccount = server.accounts().account(source.getAccountId());

		KeyPair destination = KeyPair.fromAccountId(bctx.getAddressTo());

		Transaction.Builder txBuilder = new Transaction.Builder(sourceAccount, stellarNetworkService.getNetwork())
				.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
				.setOperationFee(stellarNetworkService.getNetworkFee())
				.addOperation(
						new PaymentOperation
								.Builder(destination.getAccountId(), asset, StellarConverter.actualToString(bctx.getAmount()))
								.build()
				);

		if(bctx.getTxAux() != null) txBuilder.addMemo(Memo.text(bctx.getTxAux()));

		// build tx
		Transaction tx = txBuilder.build();

		String txHash = ByteArrayUtil.toHexString(tx.hash());

		JsonObject requestInfo = new JsonObject();
		requestInfo.addProperty("sequence", tx.getSequenceNumber());
		requestInfo.addProperty("hash", txHash);
		requestInfo.addProperty("envelopeXdr", tx.toEnvelopeXdrBase64());

		log.setRequest(GSONWriter.toJsonString(requestInfo));

		logger.info("[BCTX#{}] Request sign for {} {}", bctx.getId(), source.getAccountId(), txHash);
		signServer().signStellarTransaction(tx);

		logger.info("[BCTX#{}] Sending TX to stellar network.", bctx.getId());
		SubmitTransactionResponse txResponse = server.submitTransaction(tx);

		if(txResponse.isSuccess()) {
			log.setBcRefId(txResponse.getHash());
			log.setResponse(GSONWriter.toJsonString(txResponse));
			return true;
		} else {
			ObjectPair<String, String> resultCodes = StellarConverter.getResultCodesFromExtra(txResponse);
			log.setErrorcode(resultCodes.first());
			log.setErrormessage(resultCodes.second());
			return false;
		}
	}
}
