package io.talken.dex.governance.service.bctx.monitor.ethereum;

import io.talken.common.RunningProfile;
import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.service.ServiceStatusService;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.service.bctx.TxMonitor;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumNetworkService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.mongodb.core.MongoTemplate;
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

import static io.talken.common.persistence.jooq.Tables.BCTX_LOG;

@Service
@Scope("singleton")
public class EthereumTxMonitor extends TxMonitor<EthBlock.Block, TransactionReceipt> {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumTxMonitor.class);

	@Autowired
	private EthereumNetworkService ethNetworkService;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private ServiceStatusService<DexGovStatus> ssService;

	@Autowired
	private DSLContext dslContext;

	private static final int MAXIMUM_LOOP = 1000; // get 100 blocks per loop, for reduce crawl load.

	@PostConstruct
	private void init() {
		if(RunningProfile.isLocal()) { // destroy log db at localhost
			ssService.status().getTxMonitor().getEthereum().setLastBlock(null);
			ssService.save();

			mongoTemplate.dropCollection(EthereumTxReceiptDocument.class);
		}
	}

	private BigInteger getCursor(Web3j web3j) throws Exception {
		Optional<BigInteger> opt_lastBlock = Optional.ofNullable(ssService.status().getTxMonitor().getEthereum().getLastBlock());

		if(opt_lastBlock.isPresent()) {
			return opt_lastBlock.get();
		} else {
			logger.info("Ethereum block collection not found, collect last 10 blocks for initial data.");
			return getLatestBlockNumber(web3j).subtract(new BigInteger("10")); // initially collect 100 blocks
		}
	}

	private BigInteger getLatestBlockNumber(Web3j web3j) throws Exception {
		return web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getNumber();
	}

	@Scheduled(fixedDelay = 15000)
	private void getBlocks() {
		Web3j web3j;
		try {
			web3j = ethNetworkService.newClient();
		} catch(Exception ex) {
			logger.exception(ex, "Cannot get ethereum rpc client.");
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
				logger.trace("Ethereum latest block {} found, current cursor = {}", latestBlockNumber, cursor);

				for(int i = 0; i < MAXIMUM_LOOP && latestBlockNumber.compareTo(cursor) > 0; i++) {
					BigInteger nextCursor = cursor.add(BigInteger.ONE);

					// get next block of cursor
					EthBlock.Block block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(nextCursor), true).send().getBlock();

					if(block != null) {
						logger.verbose("Ethereum block {} contains {} tx.", block.getNumber(), block.getTransactions().size());

						callBlockHandlerStack(block);

						List<EthereumTxReceiptDocument> txReceiptDocuments = new ArrayList<>();

						if(block.getTransactions().size() > 0) {
							for(EthBlock.TransactionResult tx : block.getTransactions()) {

								org.web3j.protocol.core.methods.response.Transaction transaction = null;

								if(tx instanceof EthBlock.TransactionHash) {
									transaction = web3j.ethGetTransactionByHash((String) tx.get()).send().getTransaction().orElse(null);
								} else if(tx instanceof EthBlock.TransactionObject) {
									transaction = (org.web3j.protocol.core.methods.response.Transaction) tx.get();
								}

								if(transaction != null) {
									// FIXME : remove after switch local ethNode
									// checkBctxLog is not required on actual service
									if(checkBctxLog(transaction.getHash())) {
										Optional<TransactionReceipt> opt_receipt = web3j.ethGetTransactionReceipt(transaction.getHash()).send().getTransactionReceipt();
										if(opt_receipt.isPresent()) {
											callTxHandlerStack(opt_receipt.get());
											txReceiptDocuments.add(EthereumTxReceiptDocument.from(transaction, opt_receipt.get()));
										} else {
											logger.error("Cannot get tx receipt from network, cancel monitoring");
											break;
										}
									}
								} else {
									logger.error("Cannot extract tx from block response, cancel monitoring");
									break;
								}
							}
						}

						ssService.status().getTxMonitor().getEthereum().setLastBlock(block.getNumber());
						ssService.status().getTxMonitor().getEthereum().setLastBlockTimestamp(UTCUtil.ts2ldt(block.getTimestamp().longValue()));
						ssService.save();

						for(EthereumTxReceiptDocument txrDoc : txReceiptDocuments) {
							mongoTemplate.save(txrDoc);
						}

						cursor = block.getNumber();
					} else {
						logger.error("GetBlock {} returned null, cancel monitoring", nextCursor);
						break;
					}
				}
			}
		} catch(Exception ex) {
			logger.exception(ex);
		}
	}

	private boolean checkBctxLog(String txHash) {
		return dslContext.selectFrom(BCTX_LOG).where(BCTX_LOG.STATUS.eq(BctxStatusEnum.SENT).and(BCTX_LOG.BC_REF_ID.eq(txHash)))
				.fetchOptional().isPresent();
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
