package io.talken.dex.governance.service.bctx.monitor.stellar;

import io.talken.common.RunningProfile;
import io.talken.common.service.ServiceStatusService;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.service.bctx.TxMonitor;
import io.talken.dex.governance.service.bctx.monitor.stellar.dextask.DexTaskTransactionHandler;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
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


@Service
@Scope("singleton")
public class StellarTxMonitor extends TxMonitor<Void, TransactionResponse> {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(DexTaskTransactionHandler.class);

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private ServiceStatusService<DexGovStatus> ssService;

	private static final int TXREQUEST_LIMIT = 200;

	@PostConstruct
	private void init() {
		// reset status if local
		if(RunningProfile.isLocal()) {
			ssService.status().getTxMonitor().getStellar().setLastPagingToken(null);
			ssService.save();
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
		Optional<String> opt_status = Optional.ofNullable(ssService.status().getTxMonitor().getStellar().getLastPagingToken());

		Server server = stellarNetworkService.pickServer();

		Page<TransactionResponse> txPage;
		try {
			if(opt_status.isPresent()) {
				// 200 is maximum
				txPage = server.transactions().order(RequestBuilder.Order.ASC).cursor(opt_status.get()).limit(TXREQUEST_LIMIT).includeFailed(false).execute();
			} else {
				// get last tx for initiation
				logger.info("Stellar tx collection not found, collect last page for initial data.");
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

				ssService.status().getTxMonitor().getStellar().setLastPagingToken(txRecord.getPagingToken());
				ssService.status().getTxMonitor().getStellar().setLastTokenTimestamp(StellarConverter.toLocalDateTime(txRecord.getCreatedAt()));
				ssService.save();

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
		receiptObj.put("sourceAccount", tx.getSourceAccount());
		receiptObj.put("createdAt", tx.getCreatedAt());

		if(tx.isSuccessful()) {
			return TxReceipt.ofSuccessful(tx.getHash(), receiptObj);
		} else {
			return TxReceipt.ofFailed(tx.getHash(), receiptObj);
		}
	}
}
