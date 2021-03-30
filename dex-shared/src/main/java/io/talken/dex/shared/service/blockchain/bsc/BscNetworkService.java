package io.talken.dex.shared.service.blockchain.bsc;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.service.blockchain.ethereum.Erc20ContractInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.http.HttpService;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigInteger;

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
        final long chainId = dexSettings.getBcnode().getBsc().getChainId();
        DexSettings._Bsc settings = dexSettings.getBcnode().getBsc();
        this.mainRpcUri = settings.getMainRpcUri();
        this.client = new BscRpcClient(this.mainRpcUri);

        logger.info("Using BSC SERVICE Network : {} / {} / {}",chainId,this.client.getUri(),mainRpcUri,this.client.getClientVersion());
    }

    public BscRpcClient getClient() { return this.client; }
    public Web3j newMainRpcClient() {
        return Web3j.build(new HttpService(this.mainRpcUri));
    }
}
