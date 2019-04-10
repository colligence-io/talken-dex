package io.talken.dex.api.service;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.StellarConverter;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.exception.AssetConvertException;
import io.talken.dex.shared.exception.TokenMetaDataNotFoundException;
import io.talken.dex.shared.exception.TokenMetaLoadException;
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

	public BigDecimal convert(String fromCode, Double amount, String toCode) throws AssetConvertException, TokenMetaDataNotFoundException, TokenMetaLoadException {
		return StellarConverter.rawToActual(convertRaw(fromCode, StellarConverter.actualToRaw(amount), toCode));
	}

	public long convertRaw(String fromCode, long amountRaw, String toCode) throws AssetConvertException, TokenMetaDataNotFoundException, TokenMetaLoadException {
		return convertRaw(tmService.getAssetType(fromCode), amountRaw, tmService.getAssetType(toCode));
	}

	public long convertRaw(Asset fromType, long amountRaw, Asset toType) throws AssetConvertException, TokenMetaDataNotFoundException, TokenMetaLoadException {
		final String from = StellarConverter.toAssetCode(fromType);
		final String to = StellarConverter.toAssetCode(toType);

		// first look up TradeAggregation data
		Double rate = getClosePrice(from, to);

		if(rate != null) {
			return (long) (amountRaw * rate);
		}

		// try interchange
		for(String ic : INTERCHANGE) {
			Double ic_rate = getClosePrice(from, ic);
			if(ic_rate != null) {
				Double ic_rate2 = getClosePrice(ic, to);
				if(ic_rate2 != null) {
					rate = ic_rate * ic_rate2;
					break;
				}
			}
		}
		if(rate != null) {
			return (long) (amountRaw * rate);
		}

		// fallback to CoinMarketCap data
		rate = getExchangeRate(from, to);
		if(rate != null) {
			return (long) (amountRaw * rate);
		}

		throw new AssetConvertException(from, to);
	}

	public BigDecimal exchange(String fromCode, Double amount, String toCode) throws AssetConvertException, TokenMetaDataNotFoundException, TokenMetaLoadException {
		return StellarConverter.rawToActual(exchangeRawToFiat(fromCode, StellarConverter.actualToRaw(amount), toCode));
	}

	public long exchangeRawToFiat(String fromCode, long amountRaw, String toCode) throws AssetConvertException, TokenMetaDataNotFoundException, TokenMetaLoadException {
		// exchange to fiat
		if(!toCode.equalsIgnoreCase("USD") && !toCode.equalsIgnoreCase("KRW")) {
			throw new AssetConvertException(fromCode, toCode);
		}

		Double rate = getExchangeRate(fromCode, toCode);
		if(rate != null) {
			return (long) (amountRaw * rate);
		}

		// try interchange with trade aggregation data
		// ex: MOBI -> BTC -> KRW
		for(String ic : INTERCHANGE) {
			if(!ic.equals(fromCode)) {
				Double ic_rate = getClosePrice(fromCode, ic);
				if(ic_rate != null) {
					Double ic_rate2 = getExchangeRate(ic, toCode);
					if(ic_rate2 != null) {
						rate = ic_rate * ic_rate2;
						break;
					}
				}
			}
		}

		if(rate != null) {
			return (long) (amountRaw * rate);
		}

		throw new AssetConvertException(fromCode, toCode);
	}

	private Double getClosePrice(String base, String counter) throws TokenMetaDataNotFoundException {
		TokenMetaTable.Meta baseMeta = tmService.getTokenMeta(base);
		if(baseMeta.getManagedInfo() == null) return null;
		if(baseMeta.getManagedInfo().getMarketPair() == null) return null;
		if(baseMeta.getManagedInfo().getMarketPair().get(counter) == null) return null;
		return baseMeta.getManagedInfo().getMarketPair().get(counter).getPriceC();
	}

	private Double getExchangeRate(String base, String counter) throws TokenMetaDataNotFoundException {
		TokenMetaTable.Meta baseMeta = tmService.getTokenMeta(base);
		if(baseMeta.getExchangeRate() == null) return null;
		return baseMeta.getExchangeRate().get(counter);
	}
}
