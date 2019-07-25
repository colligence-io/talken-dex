package io.talken.dex.shared.service.blockchain.ethereum;


import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.service.blockchain.RandomServerPicker;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class EthereumNetworkService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumNetworkService.class);

	private final DexSettings dexSettings;

	private final RandomServerPicker serverPicker = new RandomServerPicker();

	@PostConstruct
	private void init() {
		if(dexSettings.getBcnode().getEthereum().getNetwork().equalsIgnoreCase("test")) {
			logger.info("Using Ethereum TEST Network.");
		} else {
			logger.info("Using Ethereum PUBLIC Network.");
		}
		for(String _s : dexSettings.getBcnode().getEthereum().getServerList()) {
			logger.info("Ethereum jsonrpc endpoint {} added.", _s);
			serverPicker.add(_s);
		}
	}

	public Web3j newClient() throws IOException {
		Web3j web3j = Web3j.build(new HttpService(serverPicker.pick()));
		logger.trace("Connected to Ethereum client version: " + web3j.web3ClientVersion().send().getWeb3ClientVersion());
		return web3j;
	}

	public BigInteger getGasPrice(Web3j web3j) {
		// TODO : calculate or get proper value
		return Convert.toWei("10", Convert.Unit.GWEI).toBigInteger();
	}

	public BigInteger getGasLimit(Web3j web3j) throws IOException {
		EthBlock.Block lastBlock = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock();
		return lastBlock.getGasLimit();
	}

	public BigDecimal getErc20BalanceOf(String owner, String contractAddress) throws Exception {

		Function function = StandardERC20ContractFunctions.balanceOf(owner);

		EthCall response = newClient().ethCall(
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
