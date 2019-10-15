package io.talken.dex.api.service;

import io.talken.common.persistence.jooq.tables.pojos.User;
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

	@Autowired
	private TradeWalletService twService;

	public TradeWalletResult getTradeWalletBalances(User user) throws TradeWalletCreateFailedException {
		TradeWalletInfo tw = twService.getTradeWallet(user);
		TradeWalletResult rtn = new TradeWalletResult();
		rtn.setActive(tw.isConfirmed());

		if(tw.isConfirmed()) {
			rtn.setAddress(tw.getAccountId());

			Map<String, BigDecimal> balances = new HashMap<>();

			for(AccountResponse.Balance balance : tw.getAccountResponse().getBalances()) {
				if(!(balance.getAsset() instanceof AssetTypeNative)) {
					balances.put(balance.getAssetCode(), new BigDecimal(balance.getBalance()).stripTrailingZeros());
				}
			}

			rtn.setBalances(balances);
		}

		return rtn;
	}
}
