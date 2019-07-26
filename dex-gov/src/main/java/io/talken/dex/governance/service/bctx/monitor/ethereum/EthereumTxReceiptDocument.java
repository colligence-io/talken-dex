package io.talken.dex.governance.service.bctx.monitor.ethereum;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Document("ethereum_txReceipt")
@Data
public class EthereumTxReceiptDocument {
	@Id
	private String id;

	TransactionReceipt receipt;

	public static EthereumTxReceiptDocument from(TransactionReceipt receipt) {
		EthereumTxReceiptDocument rtn = new EthereumTxReceiptDocument();
		rtn.id = receipt.getTransactionHash();
		rtn.receipt = receipt;
		return rtn;
	}
}
