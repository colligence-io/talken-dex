package io.talken.dex.governance.service.bctx.monitor.filecoin;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.exception.BctxException;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * The type Filecoin receipt collector.
 */
@Service
@Scope("singleton")
@Deprecated
public class FilecoinReceiptCollector {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(FilecoinReceiptCollector.class);

	private final long collectionTimeout = 30;

    /**
     * Collect map.
     *
     * @param collectThreads the collect threads
     * @param networkName    the network name
     * @param web3j          the web 3 j
     * @param txs            the txs
     * @return the map
     * @throws ExecutionException   the execution exception
     * @throws InterruptedException the interrupted exception
     * @throws TimeoutException     the timeout exception
     * @throws BctxException        the bctx exception
     * @throws IOException          the io exception
     */
    @SuppressWarnings("unchecked")
	public synchronized Map<String, TransactionReceipt> collect(int collectThreads, String networkName, Web3j web3j, List<Transaction> txs) throws ExecutionException, InterruptedException, TimeoutException, BctxException, IOException {
		Map<String, TransactionReceipt> receipts = new HashMap<>();
		if(txs.size() > 0) {
			Queue<String> tq = new ConcurrentLinkedQueue<>();

			// if queue capacity is not enough Queue.add will throw exception
			for(Transaction tx : txs) tq.add(tx.getHash());

			// prepare collectors list
			List<Future> collectors = new ArrayList<>();

			for(int i = 0; i < Math.max(1, Math.min(collectThreads, txs.size() / 10)); i++) {
				collectors.add(collectReceipts(networkName, web3j, tq));
			}

			try {
				// wait for all collectors finished
				for(Future collector : collectors) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    HashMap<String, TransactionReceipt> t_receipts = objectMapper.convertValue(collector.get(collectionTimeout, TimeUnit.SECONDS), HashMap.class);
//					HashMap<String, TransactionReceipt> t_receipts = (HashMap) collector.get(collectionTimeout, TimeUnit.SECONDS);
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

    /**
     * Collect receipts future.
     *
     * @param networkName the network name
     * @param web3j       the web 3 j
     * @param tq          the tq
     * @return the future
     * @throws BctxException the bctx exception
     * @throws IOException   the io exception
     */
    @Async
	public Future<Map<String, TransactionReceipt>> collectReceipts(String networkName, Web3j web3j, Queue<String> tq) throws BctxException, IOException {
		Map<String, TransactionReceipt> t_receipts = new HashMap<>();
		while(true) {
			final String hash = tq.poll();
			if(hash == null) break;

			Optional<TransactionReceipt> opt_receipt = web3j.ethGetTransactionReceipt(hash).send().getTransactionReceipt();

			if(!opt_receipt.isPresent())
				throw new BctxException("ReceiptNotNound", "Cannot get tx receipt from " + networkName + " network, cancel monitoring");

			t_receipts.put(hash, opt_receipt.get());
		}
		return new AsyncResult<>(t_receipts);
	}
}
