package io.talken.dex.api.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;

@Data
@Builder
public class EthTransactionResultDTO {
    private String hash;
    private BigInteger nonce;
    private String blockHash;
    private BigInteger blockNumber;
    private BigInteger transactionIndex;
    private String from;
    private String to;
    private BigInteger value;
    private BigInteger gasPrice;
    private BigInteger gas;
    private String input;
    private String raw;
    private String r;
    private String s;
    private long v;

    private String creates;
    private long chainId;
    private String publicKey;
}
