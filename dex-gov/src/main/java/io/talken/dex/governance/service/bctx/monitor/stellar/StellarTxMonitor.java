package io.talken.dex.governance.service.bctx.monitor.stellar;

import io.talken.common.RunningProfile;
import io.talken.common.persistence.jooq.tables.records.DexGovStatusRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.bctx.TxMonitor;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Server;
import org.stellar.sdk.requests.RequestBuilder;
import org.stellar.sdk.responses.Page;
import org.stellar.sdk.responses.TransactionResponse;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.DEX_GOV_STATUS;

@Service
@Scope("singleton")
public class StellarTxMonitor extends TxMonitor<Void, TransactionResponse> {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(DexTaskTransactionHandler.class);

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private DSLContext dslContext;

	private static final int TXREQUEST_LIMIT = 200;

	@PostConstruct
	private void init() {
		// reset status if local
		if(RunningProfile.isLocal()) {
			dslContext.deleteFrom(DEX_GOV_STATUS).where().execute();
		}
	}

	@Scheduled(fixedDelay = 4000, initialDelay = 10000)
	private void checkTask() {
		int processed = -1;
		do {
			processed = processNextTransactions();
		} while(processed == TXREQUEST_LIMIT);
	}

	private int processNextTransactions() {
		Optional<DexGovStatusRecord> opt_status = dslContext.selectFrom(DEX_GOV_STATUS).limit(1).fetchOptional();

		Server server = stellarNetworkService.pickServer();

		Page<TransactionResponse> txPage;
		try {
			if(opt_status.isPresent()) {
				// 200 is maximum
				txPage = server.transactions().order(RequestBuilder.Order.ASC).cursor(opt_status.get().getTxmonitorlastpagingtoken()).limit(TXREQUEST_LIMIT).includeFailed(false).execute();
			} else {
				// insert initial row
				dslContext.insertInto(DEX_GOV_STATUS).columns(DEX_GOV_STATUS.TXMONITORLASTPAGINGTOKEN).values("0").execute();
				// get last tx for initiation
				txPage = server.transactions().order(RequestBuilder.Order.DESC).limit(1).includeFailed(false).execute();
			}
		} catch(Exception ex) {
			logger.exception(ex, "Cannot get last tx from stellar network.");
			return -1;
		}

		return processTransactionPage(txPage);
	}

	private int processTransactionPage(Page<TransactionResponse> txPage) {
		int processed = 0;

		for(TransactionResponse txRecord : txPage.getRecords()) {
			try {

				callTxHandlerStack(txRecord);

				// mark last checked tx
				dslContext.update(DEX_GOV_STATUS)
						.set(DEX_GOV_STATUS.TXMONITORLASTPAGINGTOKEN, txRecord.getPagingToken())
						.execute();

				processed++;
			} catch(Exception ex) {
				logger.exception(ex, "Unidentified exception occured.");
			}
		}

		return processed;
	}

	@Override
	protected TxReceipt toTxMonitorReceipt(TransactionResponse tx) {

		Map<String, Object> receiptObj = new HashMap<>();

		receiptObj.put("txHash", tx.getHash());
		receiptObj.put("ledger", tx.getLedger());
		receiptObj.put("resultXdr", tx.getResultXdr());
		receiptObj.put("envelopeXdr", tx.getEnvelopeXdr());
		receiptObj.put("resultMetaXdr", tx.getResultMetaXdr());
		receiptObj.put("feePaid", tx.getFeePaid());
		receiptObj.put("sourceAccount", tx.getSourceAccount().getAccountId());
		receiptObj.put("createdAt", tx.getCreatedAt());

		if(tx.isSuccessful()) {
			return TxReceipt.ofSuccessful(tx.getHash(), receiptObj);
		} else {
			return TxReceipt.ofFailed(tx.getHash(), receiptObj);
		}
	}
}
