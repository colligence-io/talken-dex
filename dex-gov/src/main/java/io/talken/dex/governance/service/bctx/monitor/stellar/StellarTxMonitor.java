package io.talken.dex.governance.service.bctx.monitor.stellar;

import io.talken.common.RunningProfile;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.service.ServiceStatusService;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.service.bctx.TxMonitor;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import io.talken.dex.shared.service.blockchain.stellar.StellarTxReceipt;
import io.talken.dex.shared.service.blockchain.stellar.StellarTxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Server;
import org.stellar.sdk.requests.ErrorResponse;
import org.stellar.sdk.requests.RequestBuilder;
import org.stellar.sdk.responses.Page;
import org.stellar.sdk.responses.TransactionResponse;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


@Service
@Scope("singleton")
public class StellarTxMonitor extends TxMonitor<Void, StellarTxResult, StellarTxReceipt> {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(DexTaskTransactionHandler.class);

	@Autowired
	private StellarNetworkService stellarNetworkService;

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

	@Scheduled(fixedDelay = 3000, initialDelay = 5000)
	private void checkTask() {
		int processed = -1;
		do {
			processed = processNextTransactions();
		} while(processed == TXREQUEST_LIMIT);
	}

	@Override
	public BlockChainPlatformEnum[] getBcTypes() {
		return new BlockChainPlatformEnum[]{BlockChainPlatformEnum.STELLAR, BlockChainPlatformEnum.STELLAR_TOKEN};
	}

	@Override
	protected StellarTxResult getTransactionReceipt(String txId) {
		Server server = stellarNetworkService.pickServer();

		try {
			new StellarTxResult(server.transactions().transaction(txId), stellarNetworkService.getNetwork());
		} catch(ErrorResponse ex) {
			if(ex.getCode() == 404) logger.debug("Stellar Tx {} is not found.", txId);
			else logger.exception(ex);
		} catch(Exception ex) {
			logger.exception(ex);
		}
		return null;
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
				StellarTxResult txResult = new StellarTxResult(txRecord, stellarNetworkService.getNetwork());
				callTxHandlerStack(txResult);

				for(StellarTxReceipt payment : txResult.getPaymentReceipts()) {
					callReceiptHandlerStack(payment);
				}

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
	protected TxReceipt toTxMonitorReceipt(StellarTxResult txResult) {

		Map<String, Object> receiptObj = new HashMap<>();

		receiptObj.put("txHash", txResult.getResponse().getHash());
		receiptObj.put("ledger", txResult.getResponse().getLedger());
		receiptObj.put("resultXdr", txResult.getResponse().getResultXdr());
		receiptObj.put("envelopeXdr", txResult.getResponse().getEnvelopeXdr());
		receiptObj.put("resultMetaXdr", txResult.getResponse().getResultMetaXdr());
		receiptObj.put("feePaid", txResult.getResponse().getFeePaid());
		receiptObj.put("sourceAccount", txResult.getResponse().getSourceAccount());
		receiptObj.put("createdAt", txResult.getResponse().getCreatedAt());

		if(txResult.getResponse().isSuccessful()) {
			return TxReceipt.ofSuccessful(txResult.getResponse().getHash(), receiptObj);
		} else {
			return TxReceipt.ofFailed(txResult.getResponse().getHash(), receiptObj);
		}
	}
}
