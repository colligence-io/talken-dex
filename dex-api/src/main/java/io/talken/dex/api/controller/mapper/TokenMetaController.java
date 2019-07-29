package io.talken.dex.api.controller.mapper;

import io.talken.common.exception.TalkenException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.DexResponse;
import io.talken.dex.api.controller.RequestMappings;
import io.talken.dex.api.service.TokenMetaService;
import io.talken.dex.shared.TokenMetaTable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotEmpty;

@RestController
public class TokenMetaController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TokenMetaController.class);

	@Autowired
	private TokenMetaService tmService;

	@RequestMapping(value = RequestMappings.TMS_MI_LIST, method = RequestMethod.GET)
	public DexResponse<TokenMetaTable> managedInfoList() throws TalkenException {
		return DexResponse.buildResponse(tmService.getManagedInfoList());
	}

	@RequestMapping(value = RequestMappings.TMS_TM_INFO, method = RequestMethod.GET)
	public DexResponse<TokenMetaTable.Meta> tokenMeta(@PathVariable @NotEmpty String symbol) throws TalkenException {
		return DexResponse.buildResponse(tmService.getTokenMeta(symbol));
	}

	@RequestMapping(value = RequestMappings.TMS_TM_LIST, method = RequestMethod.GET)
	public DexResponse<TokenMetaTable> tokenMetaList() throws TalkenException {
		return DexResponse.buildResponse(tmService.getTokenMetaList());
	}
}
