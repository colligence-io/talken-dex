package io.talken.dex.api.service;

import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.dto.TradeWalletResult;
import io.talken.dex.shared.exception.TradeWalletCreateFailedException;
import io.talken.dex.shared.service.blockchain.stellar.StellarOpReceipt;
import io.talken.dex.shared.service.tradewallet.TradeWalletInfo;
import io.talken.dex.shared.service.tradewallet.TradeWalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.stellar.sdk.AssetTypeNative;
import org.stellar.sdk.responses.AccountResponse;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Scope("singleton")
public class WalletService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(WalletService.class);

	@Autowired
	private TradeWalletService twService;

	@Autowired
	private MongoTemplate mongoTemplate;

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

	public List<StellarOpReceipt> getTxList(String address, String operationType, String assetCode, String assetIssuer, Sort.Direction direction, int page, int offset) {
		Criteria ct = new Criteria();

		if(address != null) {
			ct.andOperator(Criteria.where("involvedAccounts").is(address));
		}

		if(operationType != null) {
			ct.andOperator(Criteria.where("operationType").is(operationType));
		}

		if(assetCode != null) {
			if(assetIssuer != null) {
				ct.andOperator(
						Criteria.where("involvedAssets").is(assetCode + ":" + assetIssuer)
				);
			} else {
				ct.andOperator(
						Criteria.where("involvedAssets").is("native")
				);
			}
		}

		Query qry = new Query()
				.addCriteria(ct)
				.limit(offset)
				.skip(Math.max((page - 1), 0) * offset)
				.with(new Sort(direction, "timeStamp"));


		return mongoTemplate.find(qry, StellarOpReceipt.class, "stellar_opReceipt");
	}
}
