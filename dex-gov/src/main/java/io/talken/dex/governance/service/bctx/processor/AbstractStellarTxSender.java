package io.talken.dex.governance.service.bctx.processor;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.pojos.BctxLog;
import io.talken.common.util.JSONWriter;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.bctx.TxSender;
import io.talken.dex.shared.BareTxInfo;
import io.talken.dex.shared.StellarConverter;
import io.talken.dex.shared.service.StellarNetworkService;
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

	protected void sendStellarTx(Asset asset, Bctx bctx, BctxLog log) throws Exception {
		// pick horizon server
		Server server = stellarNetworkService.pickServer();

		// prepare accounts
		KeyPair source = KeyPair.fromAccountId(bctx.getAddressFrom());

		// load up-to-date information on source account.
		AccountResponse sourceAccount = server.accounts().account(source);

		KeyPair destination = KeyPair.fromAccountId(bctx.getAddressTo());

		Transaction tx = stellarNetworkService.getTransactionBuilderFor(sourceAccount)
				.addMemo(Memo.text(bctx.getTxAux()))
				.addOperation(
						new PaymentOperation
								.Builder(destination, asset, StellarConverter.actualToString(bctx.getAmount()))
								.build()
				).build();

		// build tx
		BareTxInfo bareTxInfo = BareTxInfo.build(tx);

		log.setRequest(JSONWriter.toJsonString(bareTxInfo));

		logger.debug("Request sign for {} {}", source.getAccountId(), bareTxInfo.getHash());
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
			log.setSuccessFlag(true);
			log.setResponse(JSONWriter.toJsonString(resObj));
		} else {
			SubmitTransactionResponse.Extras.ResultCodes resultCodes = txResponse.getExtras().getResultCodes();
			log.setSuccessFlag(false);
			log.setErrorcode(resultCodes.getTransactionResultCode());
			StringJoiner sj = new StringJoiner(",");
			if(resultCodes.getOperationsResultCodes() != null) resultCodes.getOperationsResultCodes().forEach(sj::add);
			log.setErrormessage(sj.toString());
		}
	}
}
