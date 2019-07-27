package io.talken.dex.governance.service.bctx.monitor.ethereum;

import io.talken.dex.shared.service.blockchain.ethereum.StandardERC20ContractFunctions;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.util.List;

@Document("ethereum_txReceipt")
@Data
public class EthereumTxReceiptDocument {
	@Id
	private String id;

	private TransactionReceipt receipt;

	private List<StandardERC20ContractFunctions.TransferEvent> erc20transfers;

	public static EthereumTxReceiptDocument from(TransactionReceipt receipt) {
		EthereumTxReceiptDocument rtn = new EthereumTxReceiptDocument();
		rtn.id = receipt.getTransactionHash();
		rtn.receipt = receipt;
		if(receipt.isStatusOK()) {
			rtn.erc20transfers = StandardERC20ContractFunctions.Decoder.getTransferEvents(receipt);
		}
		return rtn;
	}
}
