package io.talken.dex.governance.service.bctx.monitor.ethereum;

import io.talken.common.RunningProfile;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.service.ServiceStatusService;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumNetworkService;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumTransferReceipt;
import lombok.Data;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Scope("singleton")
public class EthereumTxMonitor extends AbstractEthereumTxMonitor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumTxMonitor.class);

	@Autowired
	private EthereumNetworkService ethNetworkService;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private ServiceStatusService ssService;

	private static final String COLLECTION_NAME = "ethereum_txReceipt";

	public EthereumTxMonitor() {
		super(logger, "Ethereum");
	}

	@Data
	public static class EthereumTxMonitorStatus {
		private BigInteger lastBlock;
		private LocalDateTime lastBlockTimestamp;
	}

	@PostConstruct
	private void init() {
		if(RunningProfile.isLocal()) { // destroy log db at localhost
			ssService.of(EthereumTxMonitorStatus.class).update((s) -> s.setLastBlock(null));

			mongoTemplate.dropCollection(COLLECTION_NAME);
		}

		if(!mongoTemplate.collectionExists(COLLECTION_NAME)) {
			mongoTemplate.createCollection(COLLECTION_NAME);
			mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(new Index().on("timeStamp", Sort.Direction.DESC));
			mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(new Index().on("contractAddress", Sort.Direction.ASC));
			mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(new Index().on("from", Sort.Direction.ASC));
			mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(new Index().on("to", Sort.Direction.ASC));
		}
	}

	@Override
	public BlockChainPlatformEnum[] getBcTypes() {
		return new BlockChainPlatformEnum[]{BlockChainPlatformEnum.ETHEREUM, BlockChainPlatformEnum.ETHEREUM_ERC20_TOKEN};
	}

	@Override
	protected TransactionReceipt getTransactionReceipt(String txId) {
		try {
			Web3j web3j = ethNetworkService.getLocalClient().newClient();
			TransactionReceipt opt_receipt = web3j.ethGetTransactionReceipt(txId)
                    .sendAsync().get().getTransactionReceipt().orElse(null);
			if(opt_receipt == null) {
                logger.debug("Ethereum Tx {} is not found.", txId);
            } else if(opt_receipt.getBlockNumberRaw() == null) {
                logger.debug("Ethereum Tx {} is Pending.", txId);
			}
            return opt_receipt;
		} catch(Exception ex) {
			logger.exception(ex);
		}
		return null;
	}

	@Override
	protected void saveReceiptDocuments(List<EthereumTransferReceipt> documents) {
		// ethereum receipt collection disabled
		// FIXME : if ethereum receipt needed some point, consider enable this
		// NOTE : THIS IS EXTREMELY SLOW
//		for(EthereumTransferReceipt document : documents) {
//			mongoTemplate.save(document, COLLECTION_NAME);
//		}
	}

	@Override
	protected BigInteger getServiceStatusLastBlock() {
		return ssService.of(EthereumTxMonitorStatus.class).read().getLastBlock();
	}

	@Override
	protected void saveServiceStatusLastBlock(BigInteger blockNumber, LocalDateTime timestamp) {
		ssService.of(EthereumTxMonitorStatus.class).update((s) -> {
			s.setLastBlock(blockNumber);
			s.setLastBlockTimestamp(timestamp);
		});
	}

	@Scheduled(fixedDelay = 3000, initialDelay = 5000)
	private void getBlocks() {
		if(DexGovStatus.isStopped) return;

		Web3j web3j;
		try {
			web3j = ethNetworkService.getLocalClient().newClient();
		} catch(Exception ex) {
			logger.exception(ex, "Cannot get ethereum rpc client.");
			return;
		}

		crawlBlocks(web3j, 5);
	}
}
