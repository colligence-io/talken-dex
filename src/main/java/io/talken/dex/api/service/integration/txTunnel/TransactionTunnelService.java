package io.talken.dex.api.service.integration.txTunnel;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.DexSettings;
import io.talken.dex.api.service.integration.APIResult;
import io.talken.dex.api.service.integration.AbstractRestApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
@Scope("singleton")
public class TransactionTunnelService extends AbstractRestApiService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TransactionTunnelService.class);

	@Autowired
	private DexSettings dexSettings;

	private static String formatString;

	@PostConstruct
	private void init() {
		formatString = dexSettings.getServer().getTxtAddress() + "/v1/%s/tx/broadcast";
	}

	public APIResult<TxtServerResponse> requestTxTunnel(BlockChainPlatformEnum platform, TxtServerRequest request) {
		String url = String.format(formatString, platform.getPlatformTxTunnelType());
		return requestPost(url, request, TxtServerResponse.class);
	}
}
