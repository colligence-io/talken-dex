package io.talken.dex.shared.service.blockchain.bsc;

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
 * The type Bsc network service.
 */
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

        logger.info("Using BSC SERVICE Network : {} / {} / {}",
                "chain Id: " + chainId,
                "uri: " + this.client.getUri(),
                "client version: " + this.client.getClientVersion());
    }

    /**
     * Gets client.
     *
     * @return the client
     */
    public BscRpcClient getClient() { return this.client; }

    /**
     * New main rpc client web 3 j.
     *
     * @return the web 3 j
     */
    public Web3j newMainRpcClient() {
        return Web3j.build(new HttpService(this.mainRpcUri));
    }

    /**
     * Gets bsc transaction.
     *
     * @param txHash the tx hash
     * @return the bsc transaction
     * @throws ExecutionException   the execution exception
     * @throws InterruptedException the interrupted exception
     */
    public Transaction getBscTransaction(String txHash) throws ExecutionException, InterruptedException {
        Web3j web3j = this.client.newClient();
        return web3j.ethGetTransactionByHash(txHash).sendAsync().get().getTransaction().orElse(null);
    }

    /**
     * Gets bsc transaction receipt.
     *
     * @param txHash the tx hash
     * @return the bsc transaction receipt
     * @throws ExecutionException   the execution exception
     * @throws InterruptedException the interrupted exception
     */
    public TransactionReceipt getBscTransactionReceipt(String txHash) throws ExecutionException, InterruptedException {
        Web3j web3j = this.client.newClient();
        return web3j.ethGetTransactionReceipt(txHash).sendAsync().get().getTransactionReceipt().orElse(null);
    }
}
