package io.talken.dex.shared.service.blockchain.klaytn;

import xyz.groundx.caver_ext_kas.CaverExtKAS;

public class KlayKasRpcClient {
    private static final String URL_TH_API = "https://th-api.klaytnapi.com";
    private static final String URL_KIP17_API = "https://kip17-api.klaytnapi.com";

    private int chainId;
    private String accessKeyId;
    private String secretAccessKey;

    private CaverExtKAS client;

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

    public CaverExtKAS getClient() {
        return this.client;
    }

    public String getChainId() {
        return String.valueOf(this.chainId);
    }
}
