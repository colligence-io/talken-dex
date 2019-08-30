package io.talken.dex.shared.service.blockchain.ethereum;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.config.CacheConfig;
import lombok.Data;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;

import java.math.BigInteger;
import java.util.List;

@Service
@Scope("singleton")
public class Erc20ContractInfoService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumNetworkService.class);

	@Cacheable(value = CacheConfig.CacheNames.ETH_ERC20_CONTRACT_INFO, key = "#contractAddress")
	public Erc20ContractInfo getErc20ContractInfo(Web3j web3j, String contractAddress) throws Exception {
		Erc20ContractInfo rtn = new Erc20ContractInfo();
		rtn.setName(getName(web3j, contractAddress));
		rtn.setSymbol(getSymbol(web3j, contractAddress));
		rtn.setDecimals(getDecimals(web3j, contractAddress));
		return rtn;
	}

	@Data
	public static class Erc20ContractInfo {
		private String name;
		private String symbol;
		private BigInteger decimals;
	}

	public String getName(Web3j web3j, String contractAddress) throws Exception {
		Function function = StandardERC20ContractFunctions.name();
		EthCall response = web3j.ethCall(
				Transaction.createEthCallTransaction(
						"0x0000000000000000000000000000000000000000",
						contractAddress,
						FunctionEncoder.encode(function)
				),
				DefaultBlockParameterName.LATEST
		).send();

		List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());

		if(decoded.size() > 0) return decoded.get(0).toString();
		else return null;
	}

	public String getSymbol(Web3j web3j, String contractAddress) throws Exception {
		Function function = StandardERC20ContractFunctions.symbol();
		EthCall response = web3j.ethCall(
				Transaction.createEthCallTransaction(
						"0x0000000000000000000000000000000000000000",
						contractAddress,
						FunctionEncoder.encode(function)
				),
				DefaultBlockParameterName.LATEST
		).send();

		List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());

		if(decoded.size() > 0) return decoded.get(0).toString();
		else return null;
	}

	public BigInteger getDecimals(Web3j web3j, String contractAddress) throws Exception {
		Function function = StandardERC20ContractFunctions.decimals();
		EthCall response = web3j.ethCall(
				Transaction.createEthCallTransaction(
						"0x0000000000000000000000000000000000000000",
						contractAddress,
						FunctionEncoder.encode(function)
				),
				DefaultBlockParameterName.LATEST
		).send();

		List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());

		if(decoded.size() > 0) return ((Uint8) decoded.get(0)).getValue();
		else return null;
	}

	public BigInteger getBalanceOf(Web3j web3j, String contractAddress, String owner) throws Exception {
		Function function = StandardERC20ContractFunctions.balanceOf(owner);

		EthCall response = web3j.ethCall(
				Transaction.createEthCallTransaction(
						owner,
						contractAddress,
						FunctionEncoder.encode(function)
				),
				DefaultBlockParameterName.LATEST
		).send();

		List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());

		if(decoded.size() > 0)
			return ((Uint256) decoded.get(0)).getValue();
		else
			return null;
	}
}
