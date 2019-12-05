package io.talken.dex.shared.service.blockchain.stellar;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;

import java.math.BigDecimal;
import java.math.BigInteger;

@Data
public class StellarTransferReceipt {
	@Id
	private String hash;
	private Long ledger;
	private String pagingToken;
	@Indexed
	private Long timeStamp;
	private String sourceAccount;
	private Long seq;
	private BigInteger feeMax;
	private BigInteger feeCharged;
	@Indexed
	private String from;
	@Indexed
	private String to;
	private BigInteger amountRaw;
	private String assetType;
	private String tokenSymbol;
	private String tokenIssuer;
	private String txMemo;
}
