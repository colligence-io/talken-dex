package io.talken.dex.api.service;

import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.dto.TradeWalletResult;
import io.talken.dex.shared.exception.TradeWalletCreateFailedException;
import io.talken.dex.shared.service.tradewallet.TradeWalletInfo;
import io.talken.dex.shared.service.tradewallet.TradeWalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.AssetTypeNative;
import org.stellar.sdk.responses.AccountResponse;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@Scope("singleton")
public class WalletService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(WalletService.class);

	@Autowired
	private TradeWalletService twService;

	public TradeWalletResult getTradeWalletBalances(User user) throws TradeWalletCreateFailedException {
		TradeWalletInfo tw = twService.getTradeWallet(user);
		TradeWalletResult rtn = new TradeWalletResult();
		Map<String, BigDecimal> balances = new HashMap<>();

		if(tw.isConfirmed()) {
			for(AccountResponse.Balance balance : tw.getAccountResponse().getBalances()) {
				if(!(balance.getAsset() instanceof AssetTypeNative)) {
					balances.put(balance.getAssetCode(), new BigDecimal(balance.getBalance()).stripTrailingZeros());
				}
			}
		}

		rtn.setActive(tw.isConfirmed());
		rtn.setAddress(tw.getAccountId());
		rtn.setBalances(balances);

		return rtn;
	}
}
