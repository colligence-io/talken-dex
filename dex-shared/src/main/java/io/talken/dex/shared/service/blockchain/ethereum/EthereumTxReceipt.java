package io.talken.dex.shared.service.blockchain.ethereum;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;

import java.math.BigInteger;

@Data
public class EthereumTxReceipt {
	@Id
	private String hash;
	private String blockNumber;
	private String blockHash;
	@Indexed
	private BigInteger timeStamp;// only from block
	private String nonce;// only from tx
	@Indexed
	private String contractAddress;
	@Indexed
	private String from;
	@Indexed
	private String to;
	private String value;
	private String transactionIndex;
	private String tokenName;
	private String tokenSymbol;
	private String tokenDecimal;
	private String gas;// only from tx
	private String gasPrice; // only from tx
	private String gasUsed;
	private String cumulativeGasUsed;
	private String input;
	private String isError;
	private String txreceipt_status;
}