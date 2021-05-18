package io.talken.dex.shared.service.blockchain.klaytn;

import xyz.groundx.caver_ext_kas.CaverExtKAS;

/**
 * The type Klay kas rpc client.
 */
public class KlayKasRpcClient {
    private static final String URL_TH_API = "https://th-api.klaytnapi.com";
    private static final String URL_KIP17_API = "https://kip17-api.klaytnapi.com";

    private int chainId;
    private String accessKeyId;
    private String secretAccessKey;

    private CaverExtKAS client;

    /**
     * Instantiates a new Klay kas rpc client.
     *
     * @param chainId         the chain id
     * @param accessKeyId     the access key id
     * @param secretAccessKey the secret access key
     */
    public KlayKasRpcClient(int chainId, String accessKeyId, String secretAccessKey) {
        this.chainId = chainId;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;

        this.client = new CaverExtKAS();
        this.client.initKASAPI(chainId, accessKeyId, secretAccessKey);
        // reset baseURL for CaverExtKAS library bug
        this.client.kas.initTokenHistoryAPI(String.valueOf(chainId), accessKeyId, secretAccessKey, URL_TH_API);
        this.client.kas.initKIP17API(String.valueOf(chainId), accessKeyId, secretAccessKey, URL_KIP17_API);
    }

    /**
     * Gets client.
     *
     * @return the client
     */
    public CaverExtKAS getClient() {
        return this.client;
    }

    /**
     * Gets chain id.
     *
     * @return the chain id
     */
    public String getChainId() {
        return String.valueOf(this.chainId);
    }
}
