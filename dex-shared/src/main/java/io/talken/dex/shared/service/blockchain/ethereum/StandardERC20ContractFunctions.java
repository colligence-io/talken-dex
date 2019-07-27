package io.talken.dex.shared.service.blockchain.ethereum;

import io.talken.common.util.PrefixedLogger;
import lombok.Data;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public abstract class StandardERC20ContractFunctions {
	public static Function totalSupply() {
		return new Function(
				"totalSupply",
				Collections.emptyList(),
				Collections.singletonList(new TypeReference<Uint256>() {}));
	}

	public static Function balanceOf(String owner) {
		return new Function(
				"balanceOf",
				Collections.singletonList(new Address(owner)),
				Collections.singletonList(new TypeReference<Uint256>() {}));
	}

	public static Function transfer(String to, BigInteger value) {
		return new Function(
				"transfer",
				Arrays.asList(new Address(to), new Uint256(value)),
				Collections.singletonList(new TypeReference<Bool>() {}));
	}

	public static Function allowance(String owner, String spender) {
		return new Function(
				"allowance",
				Arrays.asList(new Address(owner), new Address(spender)),
				Collections.singletonList(new TypeReference<Uint256>() {}));
	}

	public static Function approve(String spender, BigInteger value) {
		return new Function(
				"approve",
				Arrays.asList(new Address(spender), new Uint256(value)),
				Collections.singletonList(new TypeReference<Bool>() {}));
	}

	public static Function transferFrom(String from, String to, BigInteger value) {
		return new Function(
				"transferFrom",
				Arrays.asList(new Address(from), new Address(to), new Uint256(value)),
				Collections.singletonList(new TypeReference<Bool>() {}));
	}

	public static Event transferEvent() {
		return new Event(
				"Transfer",
				Arrays.asList(
						new TypeReference<Address>(true) {},
						new TypeReference<Address>(true) {},
						new TypeReference<Uint256>() {}));
	}

	public static Event approvalEvent() {
		return new Event(
				"Approval",
				Arrays.asList(
						new TypeReference<Address>(true) {},
						new TypeReference<Address>(true) {},
						new TypeReference<Uint256>() {}));
	}

	public static class Decoder {
		private static final PrefixedLogger logger = PrefixedLogger.getLogger(Decoder.class);

		private static final Event transferEvent = StandardERC20ContractFunctions.transferEvent();
		private static final String encodedTransferEventSignature = EventEncoder.encode(transferEvent);

		public static List<TransferEvent> getTransferEvents(TransactionReceipt receipt) {
			List<TransferEvent> rtn = new ArrayList<>();

			if(receipt.getLogs() != null && receipt.getLogs().size() > 0) {
				for(Log log : receipt.getLogs()) {
					if(log.getTopics() != null && log.getTopics().size() == 3 && log.getTopics().get(0).equals(encodedTransferEventSignature)) {
						try {
							List<Type> values = FunctionReturnDecoder.decode(log.getData(), transferEvent.getNonIndexedParameters());
							if(values != null && values.size() > 0 && values.get(0) instanceof Uint256) {
								rtn.add(
										new TransferEvent(
												new Address(log.getTopics().get(1)).toString(),
												new Address(log.getTopics().get(2)).toString(),
												((Uint256) values.get(0)).getValue()
										)
								);
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
	public static class TransferEvent {
		String from;
		String to;
		BigInteger amount;

		private TransferEvent(String from, String to, BigInteger amount) {
			this.from = from;
			this.to = to;
			this.amount = amount;
		}
	}
}
