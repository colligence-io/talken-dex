package io.talken.dex.governance.service.bctx.monitor.luniverse;

import io.talken.dex.shared.service.blockchain.ethereum.StandardERC20ContractFunctions;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.util.List;

@Document("luniverse_txReceipt")
@Data
public class LuniverseTxReceiptDocument {
	@Id
	private String id;

	private Transaction tx;
	private TransactionReceipt receipt;

	private List<StandardERC20ContractFunctions.TransferEvent> erc20transfers;

	public static LuniverseTxReceiptDocument from(Transaction tx, TransactionReceipt receipt) {
		LuniverseTxReceiptDocument rtn = new LuniverseTxReceiptDocument();
		rtn.id = tx.getHash();
		rtn.tx = tx;
		rtn.receipt = receipt;
		if(receipt.isStatusOK()) {
			rtn.erc20transfers = StandardERC20ContractFunctions.Decoder.getTransferEvents(receipt);
		}
		return rtn;
	}
}
