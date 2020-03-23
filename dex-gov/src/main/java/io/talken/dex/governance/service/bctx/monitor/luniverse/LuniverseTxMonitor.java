package io.talken.dex.governance.service.bctx.monitor.luniverse;

import io.talken.common.RunningProfile;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.service.ServiceStatusService;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.service.bctx.monitor.ethereum.AbstractEthereumTxMonitor;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumTransferReceipt;
import io.talken.dex.shared.service.blockchain.luniverse.LuniverseNetworkService;
import lombok.Data;
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
import java.util.Optional;

@Service
@Scope("singleton")
public class LuniverseTxMonitor extends AbstractEthereumTxMonitor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(LuniverseTxMonitor.class);

	@Autowired
	private LuniverseNetworkService luniverseNetworkService;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private ServiceStatusService ssService;

	@Data
	public static class LuniverseTxMonitorStatus {
		private BigInteger lastBlock;
		private LocalDateTime lastBlockTimestamp;
	}

	private static final String COLLECTION_NAME = "luniverse_txReceipt";

	public LuniverseTxMonitor() {
		super(logger, "Luniverse");
	}

	@PostConstruct
	private void init() {
		if(RunningProfile.isLocal()) { // destroy log db at localhost
			ssService.of(LuniverseTxMonitorStatus.class).update((s) -> {
				s.setLastBlock(null);
			});

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
		return new BlockChainPlatformEnum[]{BlockChainPlatformEnum.LUNIVERSE_MAIN_TOKEN, BlockChainPlatformEnum.LUNIVERSE};
	}

	@Override
	protected TransactionReceipt getTransactionReceipt(String txId) {
		try {
			Web3j web3j = luniverseNetworkService.newMainRpcClient();
			Optional<TransactionReceipt> opt_receipt = web3j.ethGetTransactionReceipt(txId).send().getTransactionReceipt();
			if(opt_receipt.isPresent()) return opt_receipt.get();
			else logger.debug("Luniverse Tx {} is not found.", txId);
		} catch(Exception ex) {
			logger.exception(ex);
		}
		return null;
	}

	@Override
	protected void saveReceiptDocuments(List<EthereumTransferReceipt> documents) {
		for(EthereumTransferReceipt document : documents) {
			mongoTemplate.save(document, COLLECTION_NAME);
		}
	}

	@Override
	protected BigInteger getServiceStatusLastBlock() {
		return ssService.of(LuniverseTxMonitorStatus.class).read().getLastBlock();
	}

	@Override
	protected void saveServiceStatusLastBlock(BigInteger blockNumber, LocalDateTime timestamp) {
		ssService.of(LuniverseTxMonitorStatus.class).update((s) -> {
			s.setLastBlock(blockNumber);
			s.setLastBlockTimestamp(timestamp);
		});
	}

	@Scheduled(fixedDelay = 3000, initialDelay = 5000)
	private void getBlocks() {
		if(DexGovStatus.isStopped) return;

		Web3j web3j;
		try {
			web3j = luniverseNetworkService.newMainRpcClient();
		} catch(Exception ex) {
			logger.exception(ex, "Cannot get luniverse main rpc client.");
			return;
		}

		crawlBlocks(web3j, 1);
	}
}
