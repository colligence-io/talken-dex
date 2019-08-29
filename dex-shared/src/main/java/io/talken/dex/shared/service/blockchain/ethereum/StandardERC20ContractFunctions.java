package io.talken.dex.shared.service.blockchain.ethereum;

import io.talken.common.util.PrefixedLogger;
import lombok.Data;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
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
					if(log.getTopics() != null && log.getTopics().contains(encodedTransferEventSignature)) {
						try {
							List<Type> values = FunctionReturnDecoder.decode(log.getData(), transferEvent.getParameters());
							if(values != null && values.size() == 3) {
								rtn.add(
										new TransferEvent(
												((Address) values.get(0)).toString(),
												((Address) values.get(1)).toString(),
												((Uint256) values.get(2)).getValue()
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

	public static BigDecimal getErc20BalanceOf(Web3j web3j, String contractAddress, String owner) throws Exception {
		Function function = balanceOf(owner);

		EthCall response = web3j.ethCall(
				Transaction.createEthCallTransaction(
						owner,
						contractAddress,
						FunctionEncoder.encode(function)
				),
				DefaultBlockParameterName.LATEST
		).sendAsync().get();

		List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());

		if(decoded.size() > 0)
			return Convert.fromWei(decoded.get(0).getValue().toString(), Convert.Unit.ETHER);
		else
			return BigDecimal.ZERO;
	}
}
