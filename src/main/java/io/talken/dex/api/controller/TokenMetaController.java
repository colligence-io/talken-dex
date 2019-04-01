package io.talken.dex.api.controller;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.exception.DexException;
import io.talken.dex.api.shared.TokenMetaData;
import io.talken.dex.api.service.TokenMetaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotEmpty;
import java.util.Map;

@RestController
public class TokenMetaController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TokenMetaController.class);

	@Autowired
	private TokenMetaService maService;

	@RequestMapping(value = RequestMappings.TMS_MI_LIST, method = RequestMethod.GET)
	public DexResponse<Map<String, TokenMetaData>> managedInfoList() throws DexException {
		return DexResponse.buildResponse(maService.getManagedInfoList());
	}

	@RequestMapping(value = RequestMappings.TMS_TM_INFO, method = RequestMethod.GET)
	public DexResponse<TokenMetaData> tokenMeta(@PathVariable @NotEmpty String symbol) throws DexException {
		return DexResponse.buildResponse(maService.getTokenMeta(symbol));
	}

	@RequestMapping(value = RequestMappings.TMS_TM_LIST, method = RequestMethod.GET)
	public DexResponse<Map<String, TokenMetaData>> tokenMetaList() throws DexException {
		return DexResponse.buildResponse(maService.getTokenMetaList());
	}
}
