package io.talken.dex.api.controller.mapper;

import io.talken.common.exception.TalkenException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.config.auth.AuthInfo;
import io.talken.dex.api.config.auth.AuthRequired;
import io.talken.dex.api.controller.DexResponse;
import io.talken.dex.api.controller.RequestMappings;
import io.talken.dex.api.controller.dto.TradeWalletResult;
import io.talken.dex.api.service.TradeWalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TradeWalletController {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TradeWalletController.class);

	@Autowired
	private TradeWalletService twService;

	@Autowired
	private AuthInfo authInfo;

	@AuthRequired
	@RequestMapping(value = RequestMappings.TRADE_WALLET_ENSURE, method = RequestMethod.GET)
	public DexResponse<TradeWalletResult> ensureTradeWallet() throws TalkenException {
		return DexResponse.buildResponse(twService.ensureTradeWallet(authInfo.getUser()));
	}
}
