package io.talken.dex.shared.service.blockchain.filecoin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FilecoinMessage {
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Message {
        private String to;
        private String from;
        private BigDecimal value;
        private Integer nonce;
        private Integer gasLimit;
        private String gasFeeCap;
        private String gasPremium;
        private String method;
        private String params;

        @Override
        public String toString() {
            return "Message{" +
                    "to='" + to + '\'' +
                    ", from='" + from + '\'' +
                    ", value=" + value +
                    ", nonce=" + nonce +
                    ", method='" + method + '\'' +
                    ", gasLimit=" + gasLimit +
                    ", gasFeeCap=" + gasFeeCap +
                    ", gasPremium=" + gasPremium +
                    ", params=" + params +
                    '}';
        }
    }
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class SecpkMessage {
        Message message;
        Cid cid;
    }
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Ticket {
        private String VRFProof;
        private String VDFResult;
        private String VDFProof;
    }
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Block {

        private String miner;
        private List<Ticket> tickets;
        private List<Cid> parents;
        private BigInteger parentWeight;
        private Integer height;
        private Integer nonce;
        private Cid messages;
        private Cid stateRoot;
        private Cid messageReceipts;
        private Object proof;
        private Integer timestamp;
//        private String blocksig;
    }
}
