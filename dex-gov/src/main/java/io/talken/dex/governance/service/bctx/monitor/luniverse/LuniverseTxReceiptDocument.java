package io.talken.dex.governance.service.bctx.monitor.luniverse;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Document("luniverse_txReceipt")
@Data
public class LuniverseTxReceiptDocument {
	@Id
	private String id;

	TransactionReceipt receipt;

	public LuniverseTxReceiptDocument() {
	}

	public LuniverseTxReceiptDocument(TransactionReceipt receipt) {
		this.id = receipt.getTransactionHash();
		this.receipt = receipt;
	}
}
