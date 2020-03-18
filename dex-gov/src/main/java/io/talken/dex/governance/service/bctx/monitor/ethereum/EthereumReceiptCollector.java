package io.talken.dex.governance.service.bctx.monitor.ethereum;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.exception.BctxException;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Service
@Scope("singleton")
public class EthereumReceiptCollector {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumReceiptCollector.class);

	private static final int COLLECT_THREAD = 10;

	private static final ExecutorService executor = Executors.newFixedThreadPool(COLLECT_THREAD);

	public Map<String, TransactionReceipt> collect(String networkName, Web3j web3j, List<Transaction> txs) throws BctxException, IOException, ExecutionException, InterruptedException {
		Queue<String> tq = new ConcurrentLinkedQueue<>();

		// if queue capacity is not enough Queue.add will throw exception
		for(Transaction tx : txs) tq.add(tx.getHash());

		// prepare collectors list
		List<Future> collectors = new ArrayList<>();

		for(int i = 0; i < Math.min(COLLECT_THREAD, txs.size()); i++) {
			collectors.add(executor.submit(() -> {
						Map<String, TransactionReceipt> t_receipts = new HashMap<>();
						while(true) {
							final String hash = tq.poll();
							if(hash == null) break;

							Optional<TransactionReceipt> opt_receipt = web3j.ethGetTransactionReceipt(hash).send().getTransactionReceipt();

							if(!opt_receipt.isPresent())
								throw new BctxException("ReceiptNotNound", "Cannot get tx receipt from network, cancel monitoring");

							t_receipts.put(hash, opt_receipt.get());
						}
						return t_receipts;
					}
			));
		}

		// wait for all collectors finished
		Map<String, TransactionReceipt> receipts = new HashMap<>();
		for(Future collector : collectors) {
			Map<String, TransactionReceipt> t_receipts = (Map<String, TransactionReceipt>) collector.get();
			receipts.putAll(t_receipts);
			//logger.debug("{} receipts merged", t_receipts.size());
		}

		if(receipts.size() != txs.size()) {
			logger.warn("{} : collected receipts number {} is not match with tx number {}", networkName, receipts.size(), txs.size());
		}

		return receipts;
	}
}
