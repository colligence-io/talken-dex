package io.talken.dex.shared.service.blockchain.klaytn;

import com.klaytn.caver.Caver;
import com.klaytn.caver.methods.response.Quantity;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.DexSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import xyz.groundx.caver_ext_kas.CaverExtKAS;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class KlaytnNetworkService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(KlaytnNetworkService.class);

    private final DexSettings dexSettings;

    @PostConstruct
    private void init() throws IOException {
        CaverExtKAS caver = new CaverExtKAS();
        final int chainId = dexSettings.getBcnode().getKlaytn().getChainId();
        final String accessKeyId = dexSettings.getBcnode().getKlaytn().getAccessKeyId();
        final String secretAccessKey = dexSettings.getBcnode().getKlaytn().getSecretAccessKey();
        caver.initKASAPI(chainId, accessKeyId, secretAccessKey);

        Quantity response = caver.rpc.klay.getBlockNumber().send();
        logger.info("Using Klaytn {} Network : {} {}", chainId, response.getValue(), "");
    }
}
