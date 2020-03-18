package io.talken.dex.governance.service.bctx.monitor.ethereum;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.exception.BctxException;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.util.*;
import java.util.concurrent.*;

public class EthereumReceiptCollector {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumReceiptCollector.class);

	private final int collectThreads;
	private final ExecutorService executor;

	private final long collectionTimeout = 30;

	public EthereumReceiptCollector(int collectThreads) {
		this.collectThreads = collectThreads;
		this.executor = Executors.newFixedThreadPool(collectThreads);
	}

	public synchronized Map<String, TransactionReceipt> collect(String networkName, Web3j web3j, List<Transaction> txs) throws ExecutionException, InterruptedException, TimeoutException {
		Map<String, TransactionReceipt> receipts = new HashMap<>();
		if(txs.size() > 0) {
			Queue<String> tq = new ConcurrentLinkedQueue<>();

			// if queue capacity is not enough Queue.add will throw exception
			for(Transaction tx : txs) tq.add(tx.getHash());

			// prepare collectors list
			List<Future> collectors = new ArrayList<>();

			for(int i = 0; i < Math.min(collectThreads, txs.size()); i++) {
				collectors.add(executor.submit(() -> {
							Map<String, TransactionReceipt> t_receipts = new HashMap<>();
							while(true) {
								final String hash = tq.poll();
								if(hash == null) break;

								Optional<TransactionReceipt> opt_receipt = web3j.ethGetTransactionReceipt(hash).send().getTransactionReceipt();

								if(!opt_receipt.isPresent())
									throw new BctxException("ReceiptNotNound", "Cannot get tx receipt from " + networkName + " network, cancel monitoring");

								t_receipts.put(hash, opt_receipt.get());
							}
							return t_receipts;
						}
				));
			}
			try {
				// wait for all collectors finished
				for(Future collector : collectors) {
					Map<String, TransactionReceipt> t_receipts = (Map<String, TransactionReceipt>) collector.get(collectionTimeout, TimeUnit.SECONDS);
					receipts.putAll(t_receipts);
				}
			} catch(TimeoutException ex) {
				for(Future collector : collectors) {
					try {
						// interrupt all tasks
						collector.cancel(true);
					} catch(Exception ex2) {
						logger.debug("exception while cancel collecting receipt : {} {}", ex.getClass().getCanonicalName(), ex.getMessage());
						// do nothing go on
					}
				}
				throw ex;
			}
		}
		return receipts;
	}
}
