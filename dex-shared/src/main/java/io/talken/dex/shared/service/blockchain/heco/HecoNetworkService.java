package io.talken.dex.shared.service.blockchain.heco;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.DexSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

import javax.annotation.PostConstruct;
import java.util.concurrent.ExecutionException;

/**
 * The type Heco network service.
 */
@Service
@Scope("singleton")
@RequiredArgsConstructor
public class HecoNetworkService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(HecoNetworkService.class);

    private final DexSettings dexSettings;

    private HecoRpcClient client;

    private String mainRpcUri;

    @PostConstruct
    private void init() throws Exception {
        final long chainId = dexSettings.getBcnode().getHeco().getChainId();
        DexSettings._Heco settings = dexSettings.getBcnode().getHeco();
        this.mainRpcUri = settings.getMainRpcUri();
        this.client = new HecoRpcClient(this.mainRpcUri);

        logger.info("Using Huobi ECO Chain SERVICE Network : {} / {} / {}",
                "chain Id: " + chainId,
                "uri: " + this.client.getUri(),
                "client version: " + this.client.getClientVersion());
    }

    /**
     * Gets client.
     *
     * @return the client
     */
    public HecoRpcClient getClient() { return this.client; }

    /**
     * New main rpc client web 3 j.
     *
     * @return the web 3 j
     */
    public Web3j newMainRpcClient() {
        return Web3j.build(new HttpService(this.mainRpcUri));
    }

    /**
     * Gets heco transaction.
     *
     * @param txHash the tx hash
     * @return the heco transaction
     * @throws ExecutionException   the execution exception
     * @throws InterruptedException the interrupted exception
     */
    public Transaction getHecoTransaction(String txHash) throws ExecutionException, InterruptedException {
        Web3j web3j = this.client.newClient();
        return web3j.ethGetTransactionByHash(txHash).sendAsync().get().getTransaction().orElse(null);
    }

    /**
     * Gets heco transaction receipt.
     *
     * @param txHash the tx hash
     * @return the heco transaction receipt
     * @throws ExecutionException   the execution exception
     * @throws InterruptedException the interrupted exception
     */
    public TransactionReceipt getHecoTransactionReceipt(String txHash) throws ExecutionException, InterruptedException {
        Web3j web3j = this.client.newClient();
        return web3j.ethGetTransactionReceipt(txHash).sendAsync().get().getTransactionReceipt().orElse(null);
    }
}
