package io.talken.dex.api.controller.dto;

import lombok.Builder;
import lombok.Data;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.util.List;

@Data
@Builder
public class EthTransactionReceiptResult {
    private String transactionHash;
    private BigInteger transactionIndex;
    private String blockHash;
    private String blockNumber;
    private BigInteger cumulativeGasUsed;
    private BigInteger gasUsed;
    private String contractAddress;
    private String root;
    private String status;
    private String from;
    private String to;
    private List<Log> logs;
    private String logsBloom;

    private String revertReason;
    private boolean isStatus;
}
