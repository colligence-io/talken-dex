package io.talken.dex.shared.service.blockchain.luniverse;

import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.luniverse.LuniverseApiClient;
import io.talken.dex.shared.DexSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
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

	public Web3j newMainRpcClient() {
		return Web3j.build(new HttpService(this.mainRpcUri));
	}

	public Web3j newSideRpcClient() {
		return Web3j.build(new HttpService(this.sideRpcUri));
	}

	public BigInteger getGasPrice(Web3j web3j) throws IOException {
		// recommended from lambda256
		return web3j.ethGasPrice().send().getGasPrice();
	}

	public BigInteger getGasLimit(Web3j web3j) {
// 3200000 이 권장되지만, 계좌에 77 이상의 LUK가 있어야 하므로 현재 테스트 상황에서 권장값 사용 불가
		return new BigInteger("100000");
	}
}
