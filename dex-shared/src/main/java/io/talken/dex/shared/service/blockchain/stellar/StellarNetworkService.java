package io.talken.dex.shared.service.blockchain.stellar;


import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.service.blockchain.RandomServerPicker;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Network;
import org.stellar.sdk.Server;

import javax.annotation.PostConstruct;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class StellarNetworkService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(StellarNetworkService.class);

	private final DexSettings dexSettings;

	private final RandomServerPicker serverPicker = new RandomServerPicker();

	private static final int BASE_FEE = 100;

	private Network network;

	@PostConstruct
	private void init() {
		if(dexSettings.getBcnode().getStellar().getNetwork().equalsIgnoreCase("test")) {
			logger.info("Using Stellar TEST Network.");
			this.network = Network.TESTNET;
		} else {
			logger.info("Using Stellar PUBLIC Network.");
			this.network = Network.PUBLIC;
		}
		for(String _s : dexSettings.getBcnode().getStellar().getServerList()) {
			logger.info("Horizon {} added.", _s);
			serverPicker.add(_s);
		}
	}

	public Server pickServer() {
		return new Server(serverPicker.pick());
	}

	public Network getNetwork() {
		return this.network;
	}

	public int getNetworkFee() {
		return BASE_FEE;
	}
}
