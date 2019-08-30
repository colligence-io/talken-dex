package io.talken.dex.governance.service.bctx.monitor.luniverse;

import io.talken.common.RunningProfile;
import io.talken.common.service.ServiceStatusService;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.service.bctx.monitor.ethereum.AbstractEthereumTxMonitor;
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

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Scope("singleton")
public class LuniverseTxMonitor extends AbstractEthereumTxMonitor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(LuniverseTxMonitor.class);

	@Autowired
	private LuniverseNetworkService luniverseNetworkService;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private ServiceStatusService<DexGovStatus> ssService;

	private static final String COLLECTION_NAME = "luniverse_txReceipt";

	public LuniverseTxMonitor() {
		super(logger, "Luniverse");
	}

	@PostConstruct
	private void init() {
		if(RunningProfile.isLocal()) { // destroy log db at localhost
			ssService.status().getTxMonitor().getLuniverse().setLastBlock(null);
			ssService.save();

			mongoTemplate.dropCollection(COLLECTION_NAME);
			mongoTemplate.createCollection(COLLECTION_NAME);
			mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(new Index().on("timeStamp", Sort.Direction.DESC));
			mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(new Index().on("contractAddress", Sort.Direction.ASC));
			mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(new Index().on("from", Sort.Direction.ASC));
			mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(new Index().on("to", Sort.Direction.ASC));
		}
	}

	@Override
	protected void saveReceiptDocuments(List<EthereumTxReceipt> documents) {
		for(EthereumTxReceipt document : documents) {
			mongoTemplate.save(document, COLLECTION_NAME);
		}
	}

	@Override
	protected BigInteger getServiceStatusLastBlock() {
		return ssService.status().getTxMonitor().getLuniverse().getLastBlock();
	}

	@Override
	protected void saveServiceStatusLastBlock(BigInteger blockNumber, LocalDateTime timestamp) {
		ssService.status().getTxMonitor().getLuniverse().setLastBlock(blockNumber);
		ssService.status().getTxMonitor().getLuniverse().setLastBlockTimestamp(timestamp);
		ssService.save();
	}

	@Override
	protected boolean checkTxHashNeedsHandling(String txHash) {
		return true; // COLLECT ALL TRANSACTIONS
	}

	@Scheduled(fixedDelay = 15000, initialDelay = 5000)
	private void getBlocks() {
		Web3j web3j;
		try {
			web3j = luniverseNetworkService.newMainRpcClient();
		} catch(Exception ex) {
			logger.exception(ex, "Cannot get luniverse main rpc client.");
			return;
		}

		crawlBlocks(web3j);
	}
}
