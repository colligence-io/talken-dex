package io.talken.dex.api.service.integration.anchor;

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
public class AnchorServerService extends AbstractRestApiService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AnchorServerService.class);

	@Autowired
	private DexSettings dexSettings;

	private static String anchoringApiUrl;
	private static String deanchoringApiUrl;

	@PostConstruct
	private void init() {
		anchoringApiUrl = dexSettings.getServer().getAncAddress() + "/exchange/anchor/asset/anchor";
		deanchoringApiUrl = dexSettings.getServer().getAncAddress() + "/exchange/anchor/asset/deanchor";
	}

	public APIResult<AncServerAnchorResponse> requestAnchor(AncServerAnchorRequest request) {
		return requestPost(anchoringApiUrl, request, AncServerAnchorResponse.class);
	}

	public APIResult<AncServerDeanchorResponse> requestDeanchor(AncServerDeanchorRequest request) {
		return requestPost(deanchoringApiUrl, request, AncServerDeanchorResponse.class);
	}
}
