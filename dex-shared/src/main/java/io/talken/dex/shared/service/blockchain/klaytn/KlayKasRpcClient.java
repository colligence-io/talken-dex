package io.talken.dex.shared.service.blockchain.klaytn;

import xyz.groundx.caver_ext_kas.CaverExtKAS;

public class KlayKasRpcClient {
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
    }

    public CaverExtKAS getClient() {
        return this.client;
    }
}
