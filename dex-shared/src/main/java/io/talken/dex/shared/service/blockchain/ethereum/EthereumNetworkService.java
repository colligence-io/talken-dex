package io.talken.dex.shared.service.blockchain.ethereum;


import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.service.blockchain.RandomServerPicker;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import javax.annotation.PostConstruct;
import java.io.IOException;

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
		logger.debug("Connected to Ethereum client version: " + web3j.web3ClientVersion().send().getWeb3ClientVersion());
		return web3j;
	}

	public int getNetworkFee() {
		// TODO : get proper price
		return 10;
	}
}
