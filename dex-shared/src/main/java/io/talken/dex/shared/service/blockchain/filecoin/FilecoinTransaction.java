package io.talken.dex.shared.service.blockchain.filecoin;

import lombok.Data;

import java.math.BigInteger;

@Data
public class FilecoinTransaction {
    private String to;
    private String from;
    private BigInteger nonce;
    private String value;
    private BigInteger gasLimit;
    private String gasFeeCap;
    private String gasPremium;
    private Long method;
    private String params;
    private Integer version;
    private String CID;
}
