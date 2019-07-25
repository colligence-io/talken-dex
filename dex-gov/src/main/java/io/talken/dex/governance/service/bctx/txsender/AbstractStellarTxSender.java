package io.talken.dex.governance.service.bctx.txsender;

import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.pojos.BctxLog;
import io.talken.common.persistence.jooq.tables.records.BctxLogRecord;
import io.talken.common.util.JSONWriter;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.bctx.TxSender;
import io.talken.dex.shared.service.blockchain.stellar.StellarRawTxInfo;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

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
		AccountResponse sourceAccount = server.accounts().account(source);

		KeyPair destination = KeyPair.fromAccountId(bctx.getAddressTo());

		Transaction.Builder txBuilder = new Transaction.Builder(sourceAccount)
				.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
				.setOperationFee(stellarNetworkService.getNetworkFee())
				.addOperation(
						new PaymentOperation
								.Builder(destination, asset, StellarConverter.actualToString(bctx.getAmount()))
								.build()
				);

		if(bctx.getTxAux() != null) txBuilder.addMemo(Memo.text(bctx.getTxAux()));

		Transaction tx = txBuilder.build();

		// build tx
		StellarRawTxInfo stellarRawTxInfo = StellarRawTxInfo.build(tx);

		log.setRequest(JSONWriter.toJsonString(stellarRawTxInfo));

		logger.debug("Request sign for {} {}", source.getAccountId(), stellarRawTxInfo.getHash());
		signServer().signStellarTransaction(tx);

		logger.debug("Sending TX to stellar network.");
		SubmitTransactionResponse txResponse = server.submitTransaction(tx);

		Map<String, Object> resObj = new HashMap<>();
		resObj.put("hash", txResponse.getHash());
		resObj.put("ledger", txResponse.getLedger());
		resObj.put("envelope_xdr", txResponse.getEnvelopeXdr());
		resObj.put("result_xdr", txResponse.getResultXdr());
		resObj.put("extras", txResponse.getExtras());

		if(txResponse.isSuccess()) {
			log.setBcRefId(txResponse.getHash());
			log.setResponse(JSONWriter.toJsonString(resObj));
			return true;
		} else {
			SubmitTransactionResponse.Extras.ResultCodes resultCodes = txResponse.getExtras().getResultCodes();
			StringJoiner sj = new StringJoiner(",");
			if(resultCodes.getOperationsResultCodes() != null) resultCodes.getOperationsResultCodes().forEach(sj::add);

			log.setErrorcode(resultCodes.getTransactionResultCode());
			log.setErrormessage(sj.toString());
			return false;
		}
	}
}
