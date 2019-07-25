package io.talken.dex.governance.service.bctx.monitor.luniverse;

import io.talken.common.RunningProfile;
import io.talken.common.util.JSONWriter;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.bctx.TxMonitor;
import io.talken.dex.shared.service.blockchain.luniverse.LuniverseNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Scope("singleton")
public class LuniverseTxMonitor extends TxMonitor<EthBlock.Block, TransactionReceipt> {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(LuniverseTxMonitor.class);

	@Autowired
	private LuniverseNetworkService luniverseNetworkService;

	@Autowired
	private MongoTemplate mongoTemplate;

	private static final int MAXIMUM_LOOP = 1000; // get 100 blocks per loop, for reduce crawl load.

	@PostConstruct
	private void init() {
		if(RunningProfile.isLocal()) { // destroy log db at localhost
			mongoTemplate.dropCollection(LuniverseBlockDocument.class);
			mongoTemplate.dropCollection(LuniverseTxReceiptDocument.class);
		}
	}

	private BigInteger getCursor(Web3j web3j) throws Exception {
		long count = mongoTemplate.count(new Query(), LuniverseBlockDocument.class);

		if(count > 0) {
			Query query = new Query();
			query.limit(1);
			query.with(new Sort(Sort.Direction.DESC, "number"));
			List<LuniverseBlockDocument> lastBlock = mongoTemplate.find(query, LuniverseBlockDocument.class);
			return lastBlock.get(0).getNumber();
		} else {
			logger.info("Luniverse block collection not found, collect last 100 blocks for initial data.");
			return getLatestBlockNumber(web3j).subtract(new BigInteger("100")); // initially collect 100 blocks
		}
	}

	private BigInteger getLatestBlockNumber(Web3j web3j) throws Exception {
		return web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getNumber();
	}

	@Scheduled(fixedDelay = 3000)
	private void getBlocks() {
		Web3j web3j;
		try {
			web3j = luniverseNetworkService.newMainRpcClient();
		} catch(Exception ex) {
			logger.exception(ex, "Cannot get luniverse rpc client.");
			return;
		}

		BigInteger cursor;
		try {
			cursor = getCursor(web3j);
		} catch(Exception ex) {
			logger.exception(ex, "Cannot get block cursor from mongodb.");
			return;
		}

		try {
			BigInteger latestBlockNumber = getLatestBlockNumber(web3j);

			if(latestBlockNumber.compareTo(cursor) > 0) { // higher block found
				logger.trace("Luniverse latest block {} found, current cursor = {}", latestBlockNumber, cursor);

				for(int i = 0; i < MAXIMUM_LOOP && latestBlockNumber.compareTo(cursor) > 0; i++) {
					BigInteger nextCursor = cursor.add(BigInteger.ONE);

					// get next block of cursor
					EthBlock.Block block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(nextCursor), false).send().getBlock();

					if(block != null) {
						logger.verbose("Luniverse block {} contains {} tx.", block.getNumber(), block.getTransactions().size());

						callBlockHandlerStack(block);

						List<TransactionReceipt> receipts = new ArrayList<>();

						if(block.getTransactions().size() > 0) {
							for(EthBlock.TransactionResult tx : block.getTransactions()) {

								String txHash = null;

								if(tx instanceof EthBlock.TransactionHash) {
									txHash = (String) tx.get();
								} else if(tx instanceof EthBlock.TransactionObject) {
									org.web3j.protocol.core.methods.response.Transaction transaction = (org.web3j.protocol.core.methods.response.Transaction) tx.get();
									txHash = transaction.getHash();
								}

								if(txHash != null) {
									Optional<TransactionReceipt> opt_receipt = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
									if(opt_receipt.isPresent()) {
										callTxHandlerStack(opt_receipt.get());
										receipts.add(opt_receipt.get());
									} else {
										logger.error("Cannot get tx receipt from network, cancel monitoring");
										break;
									}
								} else {
									logger.error("Cannot extract txHash from block response, cancel monitoring");
									break;
								}
							}
						}

						mongoTemplate.save(new LuniverseBlockDocument(block));
						for(TransactionReceipt receipt : receipts) {
							mongoTemplate.save(new LuniverseTxReceiptDocument(receipt));
						}

						cursor = block.getNumber();
					} else {
						logger.error("GetBlock {} returned null, cancel monitoring");
						break;
					}
				}
			}
		} catch(Exception ex) {
			logger.exception(ex);
		}
	}

	@Override
	protected TxReceipt toTxMonitorReceipt(TransactionReceipt tx) {
		if(tx.isStatusOK()) {
			return TxReceipt.ofSuccessful(tx.getTransactionHash(), JSONWriter.toJsonStringSafe(tx));
		} else {
			return TxReceipt.ofFailed(tx.getTransactionHash(), JSONWriter.toJsonStringSafe(tx));
		}
	}
}
