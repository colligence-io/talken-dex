package io.talken.dex.api.service;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.exception.common.TokenMetaNotManagedException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.exception.StellarException;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Asset;
import org.stellar.sdk.responses.OrderBookResponse;

import java.io.IOException;
import java.math.BigDecimal;

@Service
@Scope("singleton")
@Lazy
public class SwapTableService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(SwapTableService.class);

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private TokenMetaService tmService;

	@Cacheable("swapPredictSet")
	public SwapPredictSet getSwapTable(String sourceAssetCode, String targetAssetCode) throws StellarException, TokenMetaNotFoundException, TokenMetaNotManagedException {
		Asset sourceAssetType = tmService.getAssetType(sourceAssetCode);
		Asset targetAssetType = tmService.getAssetType(targetAssetCode);

		SwapPredictSet st = new SwapPredictSet();
		st.setBaseAssetCode(sourceAssetCode);
		st.setCounterAssetCode(targetAssetCode);

		try {
			OrderBookResponse s2pResponse = stellarNetworkService.pickServer()
					.orderBook()
					.sellingAsset(targetAssetType)
					.buyingAsset(sourceAssetType).execute();

			for(OrderBookResponse.Row ask : s2pResponse.getAsks()) {
				logger.trace("{} {} {} {}", sourceAssetCode, targetAssetCode, ask.getPrice(), ask.getAmount());
				st.addAsk(new BigDecimal(ask.getPrice()), new BigDecimal(ask.getAmount()));
			}
		} catch(IOException ex) {
			throw new StellarException(ex);
		}

		return st;
	}
}
