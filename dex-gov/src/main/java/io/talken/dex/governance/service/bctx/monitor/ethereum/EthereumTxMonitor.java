package io.talken.dex.governance.service.bctx.monitor.ethereum;

import io.talken.common.RunningProfile;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.service.ServiceStatusService;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumNetworkService;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumTransferReceipt;
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
import java.util.Optional;

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
	private ServiceStatusService<DexGovStatus> ssService;

	private static final String COLLECTION_NAME = "ethereum_txReceipt";

	public EthereumTxMonitor() {
		super(logger, "Ethereum");
	}

	@PostConstruct
	private void init() {
		if(RunningProfile.isLocal()) { // destroy log db at localhost
			ssService.status().getTxMonitor().getEthereum().setLastBlock(null);
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
	public BlockChainPlatformEnum[] getBcTypes() {
		return new BlockChainPlatformEnum[]{BlockChainPlatformEnum.ETHEREUM, BlockChainPlatformEnum.ETHEREUM_ERC20_TOKEN};
	}

	@Override
	protected TransactionReceipt getTransactionReceipt(String txId) {
		try {
			Web3j web3j = ethNetworkService.getLocalClient().newClient();
			Optional<TransactionReceipt> opt_receipt = web3j.ethGetTransactionReceipt(txId).send().getTransactionReceipt();
			if(opt_receipt.isPresent()) {
				TransactionReceipt receipt = opt_receipt.get();
				if(receipt.getBlockNumberRaw() != null) return opt_receipt.get();
			} else logger.debug("Ethereum Tx {} is not found.", txId);
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
		return ssService.status().getTxMonitor().getEthereum().getLastBlock();
	}

	@Override
	protected void saveServiceStatusLastBlock(BigInteger blockNumber, LocalDateTime timestamp) {
		ssService.status().getTxMonitor().getEthereum().setLastBlock(blockNumber);
		ssService.status().getTxMonitor().getEthereum().setLastBlockTimestamp(timestamp);
		ssService.save();
	}

	@Scheduled(fixedDelay = 15000, initialDelay = 5000)
	private void getBlocks() {
		Web3j web3j;
		try {
			web3j = ethNetworkService.getLocalClient().newClient();
		} catch(Exception ex) {
			logger.exception(ex, "Cannot get ethereum rpc client.");
			return;
		}

		crawlBlocks(web3j);
	}
}
