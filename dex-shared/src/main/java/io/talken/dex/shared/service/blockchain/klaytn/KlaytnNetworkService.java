package io.talken.dex.shared.service.blockchain.klaytn;

import com.klaytn.caver.methods.response.Quantity;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.DexSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;

/**
 * The type Klaytn network service.
 */
@Service
@Scope("singleton")
@RequiredArgsConstructor
public class KlaytnNetworkService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(KlaytnNetworkService.class);

    private final DexSettings dexSettings;

    private KlayKasRpcClient klayKasRpcClient;

    @PostConstruct
    private void init() throws IOException {
        final int chainId = dexSettings.getBcnode().getKlaytn().getChainId();
        final String accessKeyId = dexSettings.getBcnode().getKlaytn().getAccessKeyId();
        final String secretAccessKey = dexSettings.getBcnode().getKlaytn().getSecretAccessKey();

        this.klayKasRpcClient = new KlayKasRpcClient(chainId, accessKeyId, secretAccessKey);

        Quantity response = this.klayKasRpcClient.getClient().rpc.klay.getBlockNumber().send();
        logger.info("Using Klaytn {} Network : {} {}", chainId, response.getValue(), "");
    }

    /**
     * Gets kas client.
     *
     * @return the kas client
     */
    public KlayKasRpcClient getKasClient() {
        return this.klayKasRpcClient;
    }
}
