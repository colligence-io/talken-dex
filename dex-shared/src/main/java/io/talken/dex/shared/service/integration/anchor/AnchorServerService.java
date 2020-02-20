package io.talken.dex.shared.service.integration.anchor;

import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.IntegrationResult;
import io.talken.common.util.integration.rest.RestApiClient;

@Deprecated
public class AnchorServerService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AnchorServerService.class);

	private static String anchoringApiUrl;
	private static String deanchoringApiUrl;

	public AnchorServerService(String apiUrl) {
		anchoringApiUrl = apiUrl + "/exchange/anchor/asset/anchor";
		deanchoringApiUrl = apiUrl + "/exchange/anchor/asset/deanchor";
	}

	public IntegrationResult<AncServerAnchorResponse> requestAnchor(AncServerAnchorRequest request) {
		return RestApiClient.requestPost(anchoringApiUrl, request, AncServerAnchorResponse.class);
	}

	public IntegrationResult<AncServerDeanchorResponse> requestDeanchor(AncServerDeanchorRequest request) {
		return RestApiClient.requestPost(deanchoringApiUrl, request, AncServerDeanchorResponse.class);
	}
}
