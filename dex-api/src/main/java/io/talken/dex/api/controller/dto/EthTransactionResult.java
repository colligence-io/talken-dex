package io.talken.dex.api.controller.dto;

import lombok.Builder;
import lombok.Data;

/**
 * The type Eth transaction result.
 */
@Data
@Builder
public class EthTransactionResult {
    private String hash;
    private String nonce;
    private String blockHash;
    private String blockNumber;
    private String transactionIndex;
    private String from;
    private String to;
    private String value;
    private String gasPrice;
    private String gas;
    private String input;
    private String raw;
    private String r;
    private String s;
    private long v;

    private String creates;
    private long chainId;
    private String publicKey;
}
