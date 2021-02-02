package io.talken.dex.governance.service.bctx.monitor.filecoin;

import io.talken.common.RunningProfile;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.service.ServiceStatusService;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.shared.service.blockchain.filecoin.FilecoinMessage;
import io.talken.dex.shared.service.blockchain.filecoin.FilecoinNetworkService;
import io.talken.dex.shared.service.blockchain.filecoin.FilecoinRpcClient;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Scope("singleton")
public class FilecoinTxMonitor extends AbstractFilecoinTxMonitor {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(FilecoinTxMonitor.class);

    public FilecoinTxMonitor() {
        super(logger, "Filecoin");
    }

    @Autowired
    private FilecoinNetworkService filecoinNetworkService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ServiceStatusService ssService;

    private static final String COLLECTION_NAME = "filecoin_txReceipt";

    @Override
    protected BigInteger getServiceStatusLastBlock() {
        return ssService.of(FilecoinTxMonitor.FileCoinTxMonitorStatus.class).read().getLastBlock();
    }

    @Override
    protected void saveServiceStatusLastBlock(BigInteger blockNumber, LocalDateTime timestamp) {
        ssService.of(FilecoinTxMonitor.FileCoinTxMonitorStatus.class).update((s) -> {
            s.setLastBlock(blockNumber);
            s.setLastBlockTimestamp(timestamp);
        });
    }

    @Override
    protected void saveReceiptDocuments(List<FilecoinMessage.SecpkMessage> documents) {
        // filecoin receipt collection disabled
    }

    @PostConstruct
    private void init() {
        if(RunningProfile.isLocal()) { // destroy log db at localhost
            ssService.of(FilecoinTxMonitor.FileCoinTxMonitorStatus.class).update((s) -> s.setLastBlock(null));

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
        return new BlockChainPlatformEnum[]{BlockChainPlatformEnum.FILECOIN};
    }

    @Override
    protected FilecoinMessage.SecpkMessage getTransactionReceipt(String txId) {
        return null;
    }

    @Data
    public static class FileCoinTxMonitorStatus {
        private BigInteger lastBlock;
        private LocalDateTime lastBlockTimestamp;
    }

    @Scheduled(fixedDelay = 3000, initialDelay = 5000)
    private void getBlocks() {
        if (DexGovStatus.isStopped) return;

        FilecoinRpcClient client = filecoinNetworkService.getClient();

        crawlBlocks(client, 1);

    }
}
