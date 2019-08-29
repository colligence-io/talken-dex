package io.talken.dex.governance.service.bctx.monitor.luniverse;

import io.talken.common.RunningProfile;
import io.talken.common.service.ServiceStatusService;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.service.bctx.TxMonitor;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumTxReceipt;
import io.talken.dex.shared.service.blockchain.luniverse.LuniverseNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
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

	@Autowired
	private ServiceStatusService<DexGovStatus> ssService;

	private static final int MAXIMUM_LOOP = 1000; // get 100 blocks per loop, for reduce crawl load.
	private static final String COLLECTION_NAME = "luniverse_txReceipt";


	@PostConstruct
	private void init() {
		if(RunningProfile.isLocal()) { // destroy log db at localhost
			ssService.status().getTxMonitor().getLuniverse().setLastBlock(null);
			ssService.save();

			mongoTemplate.dropCollection(COLLECTION_NAME);
			mongoTemplate.createCollection(COLLECTION_NAME);
			mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(new Index().on("transfers.contract", Sort.Direction.ASC));
			mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(new Index().on("transfers.from", Sort.Direction.ASC));
			mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(new Index().on("transfers.to", Sort.Direction.ASC));
		}
	}

	private BigInteger getCursor(Web3j web3j) throws Exception {
		Optional<BigInteger> opt_lastBlock = Optional.ofNullable(ssService.status().getTxMonitor().getLuniverse().getLastBlock());

		if(opt_lastBlock.isPresent()) {
			return opt_lastBlock.get();
		} else {
			logger.info("Luniverse block collection not found, collect last 10 blocks for initial data.");
			return getLatestBlockNumber(web3j).subtract(new BigInteger("10")); // initially collect 100 blocks
		}
	}

	private BigInteger getLatestBlockNumber(Web3j web3j) throws Exception {
		return web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getNumber();
	}

	@Scheduled(fixedDelay = 3000, initialDelay = 5000)
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
					EthBlock.Block block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(nextCursor), true).send().getBlock();

					if(block != null) {
						logger.verbose("Luniverse block {} contains {} tx.", block.getNumber(), block.getTransactions().size());

						callBlockHandlerStack(block);

						List<EthereumTxReceipt> txReceiptDocuments = new ArrayList<>();

						if(block.getTransactions().size() > 0) {
							for(EthBlock.TransactionResult tx : block.getTransactions()) {

								org.web3j.protocol.core.methods.response.Transaction transaction = null;

								if(tx instanceof EthBlock.TransactionHash) {
									transaction = web3j.ethGetTransactionByHash((String) tx.get()).send().getTransaction().orElse(null);
								} else if(tx instanceof EthBlock.TransactionObject) {
									transaction = (org.web3j.protocol.core.methods.response.Transaction) tx.get();
								}

								if(transaction != null) {
									Optional<TransactionReceipt> opt_receipt = web3j.ethGetTransactionReceipt(transaction.getHash()).send().getTransactionReceipt();
									if(opt_receipt.isPresent()) {
										callTxHandlerStack(opt_receipt.get());
										txReceiptDocuments.add(EthereumTxReceipt.from(transaction, opt_receipt.get()));
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

						ssService.status().getTxMonitor().getLuniverse().setLastBlock(block.getNumber());
						ssService.status().getTxMonitor().getLuniverse().setLastBlockTimestamp(UTCUtil.ts2ldt(block.getTimestamp().longValue()));
						ssService.save();

						for(EthereumTxReceipt txrDoc : txReceiptDocuments) {
							mongoTemplate.save(txrDoc, COLLECTION_NAME);
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
			return TxReceipt.ofSuccessful(tx.getTransactionHash(), tx);
		} else {
			return TxReceipt.ofFailed(tx.getTransactionHash(), tx);
		}
	}
}
