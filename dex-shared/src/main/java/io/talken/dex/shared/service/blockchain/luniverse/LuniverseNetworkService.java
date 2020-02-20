package io.talken.dex.shared.service.blockchain.luniverse;

import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.luniverse.LuniverseApiClient;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.service.blockchain.ethereum.Erc20ContractInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.http.HttpService;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigInteger;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class LuniverseNetworkService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(LuniverseNetworkService.class);

	private final DexSettings dexSettings;

	private final Erc20ContractInfoService erc20ContractInfoService;

	private LuniverseApiClient client;

	private String mainRpcUri;
	private String sideRpcUri;

	@PostConstruct
	private void init() {
		DexSettings._Luniverse settings = dexSettings.getBcnode().getLuniverse();
		String apiUri = settings.getApiUri();
		this.mainRpcUri = settings.getMainRpcUri();
		this.sideRpcUri = settings.getSideRpcUri();
		this.client = new LuniverseApiClient(apiUri, settings.getSecret().getApiKey());
		logger.info("Using Luniverse SERVICE Network : {}", apiUri);
	}

	/**
	 * Luniverse TxAPI client
	 *
	 * @return
	 */
	public LuniverseApiClient getClient() {
		return client;
	}

	/**
	 * Luniverse main RPC Client
	 *
	 * @return
	 */
	public Web3j newMainRpcClient() {
		return Web3j.build(new HttpService(this.mainRpcUri));
	}

	/**
	 * Luniverse side RPC Client
	 *
	 * @return
	 */
	public Web3j newSideRpcClient() {
		return Web3j.build(new HttpService(this.sideRpcUri));
	}

	/**
	 * get Luniverse gas price
	 *
	 * @param web3j
	 * @return
	 * @throws IOException
	 */
	public BigInteger getGasPrice(Web3j web3j) throws IOException {
		// recommended from lambda256
		return web3j.ethGasPrice().send().getGasPrice();
	}

	/**
	 * get luniverse gas limit
	 *
	 * @param web3j
	 * @return
	 */
	public BigInteger getGasLimit(Web3j web3j) {
// 3200000 이 권장되지만, 계좌에 77 이상의 LUK가 있어야 하므로 현재 테스트 상황에서 권장값 사용 불가
		return new BigInteger("100000");
	}

	/**
	 * get luniverse balance for asset
	 *
	 * @param address
	 * @param contractAddress null for LUK (native)
	 * @return
	 */
	public BigInteger getBalance(String address, String contractAddress) {
		return getBalance(newMainRpcClient(), address, contractAddress);
	}

	/**
	 * get luniverse balance for asset
	 *
	 * @param web3j
	 * @param address
	 * @param contractAddress null for LUK (native)
	 * @return
	 */
	public BigInteger getBalance(Web3j web3j, String address, String contractAddress) {
		try {
			if(contractAddress != null) {
				return erc20ContractInfoService.getBalanceOf(web3j, address, contractAddress);
			} else {
				return web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
			}
		} catch(Exception ex) {
			logger.exception(ex, "Cannot get balance : {} {}", address, contractAddress);
			return null;
		}
	}
}
