package io.talken.dex.shared.service.blockchain.filecoin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * The type Filecoin message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FilecoinMessage {
    /**
     * The type Message.
     */
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

    /**
     * The type Secpk message.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class SecpkMessage {
        /**
         * The Message.
         */
        Message message;
        /**
         * The Cid.
         */
        Cid cid;
    }

    /**
     * The type Ticket.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Ticket {
        private String VRFProof;
        private String VDFResult;
        private String VDFProof;
    }

    /**
     * The type Block.
     */
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
