package io.talken.dex.shared.service.blockchain.ethereum;

import io.talken.common.util.PrefixedLogger;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.core.methods.response.Log;
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
			rtn.transfers.addAll(Decoder.getTransferEvents(receipt));

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


	public static class Decoder {
		private static final PrefixedLogger logger = PrefixedLogger.getLogger(Decoder.class);

		private static final Event transferEvent = StandardERC20ContractFunctions.transferEvent();
		private static final String encodedTransferEventSignature = EventEncoder.encode(transferEvent);

		public static List<EthereumTxReceipt.EthereumTransferEventData> getTransferEvents(TransactionReceipt receipt) {
			List<EthereumTxReceipt.EthereumTransferEventData> rtn = new ArrayList<>();

			if(receipt.getLogs() != null && receipt.getLogs().size() > 0) {
				for(Log log : receipt.getLogs()) {
					if(log.getTopics() != null && log.getTopics().contains(encodedTransferEventSignature)) {
						try {
							List<Type> values = FunctionReturnDecoder.decode(log.getData(), transferEvent.getParameters());
							if(values != null && values.size() == 3) {
								EthereumTxReceipt.EthereumTransferEventData ted = new EthereumTxReceipt.EthereumTransferEventData();
								ted.setContract(receipt.getTo());
								ted.setFrom(((Address) values.get(0)).toString());
								ted.setTo(((Address) values.get(1)).toString());
								ted.setValue(((Uint256) values.get(2)).getValue());
								rtn.add(ted);
							}
						} catch(Exception ex) {
							logger.exception(ex, "Cannot decode ABI from txReceipt");
						}
					}
				}
			}

			return rtn;
		}
	}

	@Data
	public static class EthereumTransferEventData {
		private String contract;
		private String from;
		private String to;
		private BigInteger value;
	}

}
