package io.talken.dex.shared.service.blockchain.bsc;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.DexSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class BscNetworkService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(BscNetworkService.class);

    private final DexSettings dexSettings;

    private BscRpcClient client;

    private String mainRpcUri;

    @PostConstruct
    private void init() throws Exception {
        DexSettings._Bsc settings = dexSettings.getBcnode().getBsc();
        this.mainRpcUri = settings.getMainRpcUri();
        this.client = new BscRpcClient(this.mainRpcUri);
        logger.info("Using Bsc SERVICE Network : {} / {} / {}",mainRpcUri,this.client.getClientVersion());
    }

    public BscRpcClient getClient() { return this.client; }
}
