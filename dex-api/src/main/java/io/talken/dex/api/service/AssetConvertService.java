package io.talken.dex.api.service;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.exception.common.TokenMetaNotManagedException;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.exception.AssetConvertException;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Asset;

import java.math.BigDecimal;

@Service
@Scope("singleton")
public class AssetConvertService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AssetConvertService.class);

	@Autowired
	private TokenMetaService tmService;

	// interchange assets, in order
	private static final String[] INTERCHANGE = new String[]{"BTC", "ETH", "XLM", "CTX"};

	public BigDecimal convert(String fromCode, BigDecimal amount, String toCode) throws AssetConvertException, TokenMetaNotFoundException, TokenMetaNotManagedException {
		return convert(tmService.getAssetType(fromCode), amount, tmService.getAssetType(toCode));
	}

	public BigDecimal convert(Asset fromType, BigDecimal amount, Asset toType) throws AssetConvertException, TokenMetaNotFoundException {
		if(fromType.equals(toType))
			return StellarConverter.scale(amount);

		final String from = StellarConverter.toAssetCode(fromType);
		final String to = StellarConverter.toAssetCode(toType);

		// first look up TradeAggregation data
		BigDecimal rate = getClosePrice(from, to);

		if(rate == null) {
			// try interchange
			for(String ic : INTERCHANGE) {
				BigDecimal ic_rate = getClosePrice(from, ic);
				if(ic_rate != null) {
					BigDecimal ic_rate2 = getClosePrice(ic, to);
					if(ic_rate2 != null) {
						rate = ic_rate.multiply(ic_rate2);
						break;
					}
				}
			}
		}

		// fallback to CoinMarketCap data
		if(rate == null) rate = getExchangeRate(from, to);

		if(rate != null) {
			return StellarConverter.scale(amount.multiply(rate));
		} else {
			throw new AssetConvertException(from, to);
		}
	}

	public BigDecimal exchange(String fromCode, BigDecimal amount, String toCode) throws AssetConvertException, TokenMetaNotFoundException {
		if(fromCode.equals(toCode))
			return StellarConverter.scale(amount);

		// exchange to fiat
		if(!toCode.equalsIgnoreCase("USD") && !toCode.equalsIgnoreCase("KRW")) {
			throw new AssetConvertException(fromCode, toCode);
		}

		BigDecimal rate = getExchangeRate(fromCode, toCode);

		if(rate == null) {
			// try interchange with trade aggregation data
			// ex: MOBI -> BTC -> KRW
			for(String ic : INTERCHANGE) {
				if(!ic.equals(fromCode)) {
					BigDecimal ic_rate = getClosePrice(fromCode, ic);
					if(ic_rate != null) {
						BigDecimal ic_rate2 = getExchangeRate(ic, toCode);
						if(ic_rate2 != null) {
							rate = ic_rate.multiply(ic_rate2);
							break;
						}
					}
				}
			}
		}

		if(rate != null) {
			return StellarConverter.scale(amount.multiply(rate));
		} else {
			throw new AssetConvertException(fromCode, toCode);
		}
	}

	private BigDecimal getClosePrice(String base, String counter) throws TokenMetaNotFoundException {
		TokenMetaTable.Meta baseMeta = tmService.getTokenMeta(base);
		if(baseMeta.getManagedInfo() == null) return null;
		if(baseMeta.getManagedInfo().getMarketPair() == null) return null;
		if(baseMeta.getManagedInfo().getMarketPair().get(counter) == null) return null;
		return baseMeta.getManagedInfo().getMarketPair().get(counter).getPriceC();
	}

	private BigDecimal getExchangeRate(String base, String counter) throws TokenMetaNotFoundException {
		TokenMetaTable.Meta baseMeta = tmService.getTokenMeta(base);
		if(baseMeta.getExchangeRate() == null) return null;
		return baseMeta.getExchangeRate().get(counter);
	}
}
