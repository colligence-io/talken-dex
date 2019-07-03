package io.talken.dex.shared.service.blockchain.luniverse;

import io.talken.common.exception.common.RestApiErrorException;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.RestApiResult;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.service.blockchain.luniverse.dto.LuniverseWalletBalanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.utils.Convert;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class LuniverseNetworkService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(LuniverseNetworkService.class);

	private final DexSettings dexSettings;

	private LuniverseApiClient client;

	@PostConstruct
	private void init() {
		logger.info("Using Luniverse SERVICE Network.");

		DexSettings._Luniverse settings = dexSettings.getBcnode().getLuniverse();

		String api = settings.getApiUri();
		String rpc = settings.getRpcUri();

		logger.info("Luniverse API endpoint {} / {} added.", api, rpc);
		client = new LuniverseApiClient(api, settings.getSecret().getApiKey(), rpc);
	}

	public LuniverseApiClient getClient() {
		return client;
	}

	public BigDecimal getBalance(String address, String mts) throws RestApiErrorException {
		return getBalance(address, mts, null);
	}

	public BigDecimal getBalance(String address, String mts, String sts) throws RestApiErrorException {
		RestApiResult<LuniverseWalletBalanceResponse> balance = getClient().getWalletBalance(address, mts, sts);
		if(!balance.isSuccess()) throw new RestApiErrorException(balance);

		return Convert.fromWei(balance.getData().getData().getBalance(), Convert.Unit.ETHER);
	}
}
