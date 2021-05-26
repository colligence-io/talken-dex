package io.talken.dex.shared.service.blockchain.heco;

import io.talken.dex.shared.service.blockchain.ethereum.ParityNextNonceResponse;
import io.talken.dex.shared.service.blockchain.ethereum.Web3jHttpService;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;

/**
 * The type Heco rpc client.
 */
public class HecoRpcClient {
    private final String uri;
    private String client = null;
    private Boolean isParity = null;

    /**
     * Instantiates a new Heco rpc client.
     *
     * @param uri the uri
     */
    public HecoRpcClient(String uri) { this.uri = uri; }

    /**
     * Gets uri.
     *
     * @return the uri
     */
    public String getUri() {
        return uri;
    }

    /**
     * Gets client version.
     *
     * @return the client version
     * @throws IOException the io exception
     */
    public String getClientVersion() throws IOException {
        // NOTICE: error occur <= 4.8.3
        if(this.client == null) this.client = newClient().web3ClientVersion().send().getWeb3ClientVersion();
        return client;
    }

    /**
     * New client web 3 j.
     *
     * @return the web 3 j
     */
    public Web3j newClient() {
        return Web3j.build(newWeb3jService());
    }

    /**
     * New web 3 j service web 3 j service.
     *
     * @return the web 3 j service
     */
    public Web3jService newWeb3jService() {
        return new Web3jHttpService(this.uri);
    }

    /**
     * get proper next nonce from pending block
     * if node server is parity, use parity_nextNonce instead of eth_transactionCount for pending block
     *
     * @param web3jService the web 3 j service
     * @param address      the address
     * @return nonce
     * @throws Exception the exception
     */
    public BigInteger getNonce(Web3jService web3jService, String address) throws Exception {
        if(this.isParity == null) {
            getClientVersion();
            this.isParity = client.startsWith("Parity-Heco");
        }

        if(this.isParity) {
            Request<?, ParityNextNonceResponse> nonceReq = new Request<>("parity_nextNonce", Collections.singletonList(address), web3jService, ParityNextNonceResponse.class);
            return nonceReq.send().getNextNonce();
        } else {
            return Web3j.build(web3jService).ethGetTransactionCount(address, DefaultBlockParameterName.PENDING).sendAsync().get().getTransactionCount();
        }
    }
}
