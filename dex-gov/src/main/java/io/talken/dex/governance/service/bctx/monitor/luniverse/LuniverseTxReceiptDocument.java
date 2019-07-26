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

	public static LuniverseTxReceiptDocument from(TransactionReceipt receipt) {
		LuniverseTxReceiptDocument rtn = new LuniverseTxReceiptDocument();
		rtn.id = receipt.getTransactionHash();
		rtn.receipt = receipt;
		return rtn;
	}
}
