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

public class HecoRpcClient {
    private final String uri;
    private String client = null;
    private Boolean isParity = null;

    public HecoRpcClient(String uri) { this.uri = uri; }

    public String getUri() {
        return uri;
    }

    public String getClientVersion() throws IOException {
        // NOTICE: error occur <= 4.8.3
        if(this.client == null) this.client = newClient().web3ClientVersion().send().getWeb3ClientVersion();
        return client;
    }

    public Web3j newClient() {
        return Web3j.build(newWeb3jService());
    }

    public Web3jService newWeb3jService() {
        return new Web3jHttpService(this.uri);
    }

    /**
     * get proper next nonce from pending block
     * if node server is parity, use parity_nextNonce instead of eth_transactionCount for pending block
     *
     * @param web3jService
     * @param address
     * @return
     * @throws Exception
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
