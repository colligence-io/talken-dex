package io.talken.dex.shared.service.blockchain.luniverse;

import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.luniverse.LuniverseApiClient;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.service.blockchain.ethereum.StandardERC20ContractFunctions;
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
public class LuniverseNetworkService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(LuniverseNetworkService.class);

	private final DexSettings dexSettings;

	private LuniverseApiClient client;

	private String mainRpcUri;
	private String sideRpcUri;

	@PostConstruct
	private void init() {
		logger.info("Using Luniverse SERVICE Network.");

		DexSettings._Luniverse settings = dexSettings.getBcnode().getLuniverse();

		String apiUri = settings.getApiUri();
		this.mainRpcUri = settings.getMainRpcUri();
		this.sideRpcUri = settings.getSideRpcUri();

		logger.info("Luniverse API endpoint {} added.", apiUri);
		this.client = new LuniverseApiClient(apiUri, settings.getSecret().getApiKey());
	}

	public LuniverseApiClient getClient() {
		return client;
	}

	public Web3j newMainRpcClient() throws IOException {
		Web3j web3j = Web3j.build(new HttpService(this.mainRpcUri));
		logger.verbose("Connected to Main Luniverse client version: " + web3j.web3ClientVersion().send().getWeb3ClientVersion());
		return web3j;
	}

	public Web3j newSideRpcClient() throws IOException {
		Web3j web3j = Web3j.build(new HttpService(this.sideRpcUri));
		logger.verbose("Connected to Side Luniverse client version: " + web3j.web3ClientVersion().send().getWeb3ClientVersion());
		return web3j;
	}

	public BigInteger getGasPrice(Web3j web3j) throws IOException {
		// recommended from lambda256
		return web3j.ethGasPrice().send().getGasPrice();
	}

	public BigInteger getGasLimit(Web3j web3j) {
// 3200000 이 권장되지만, 계좌에 77 이상의 LUK가 있어야 하므로 현재 테스트 상황에서 권장값 사용 불가
		return new BigInteger("100000");
	}

	public BigDecimal getErc20BalanceOf(String owner, String contractAddress) throws Exception {

		Function function = StandardERC20ContractFunctions.balanceOf(owner);

		EthCall response = newMainRpcClient().ethCall(
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
