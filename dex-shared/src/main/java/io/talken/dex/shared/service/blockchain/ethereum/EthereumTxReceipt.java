package io.talken.dex.shared.service.blockchain.ethereum;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Data
public class EthereumTxReceipt {
	@Id
	private String id;
	private BigInteger blockNumber;
	private Transaction tx;
	private TransactionReceipt receipt;
	private List<EthereumTransferEventData> transfers;

	public static EthereumTxReceipt from(Transaction tx, TransactionReceipt receipt) {
		EthereumTxReceipt rtn = new EthereumTxReceipt();
		rtn.id = tx.getHash();
		rtn.blockNumber = tx.getBlockNumber();
		rtn.tx = tx;
		rtn.receipt = receipt;
		rtn.transfers = new ArrayList<>();
		if(receipt.isStatusOK()) {
			rtn.transfers.addAll(StandardERC20ContractFunctions.Decoder.getTransferEvents(receipt));

			if(tx.getValue().compareTo(BigInteger.ZERO) != 0) {
				EthereumTransferEventData ted = new EthereumTransferEventData();
				ted.setFrom(receipt.getFrom());
				ted.setTo(receipt.getTo());
				ted.setValue(tx.getValue());
				rtn.transfers.add(ted);
			}
		}
		return rtn;
	}


	@Data
	public static class EthereumTransferEventData {
		private String contract;
		private String from;
		private String to;
		private BigInteger value;
	}

}
