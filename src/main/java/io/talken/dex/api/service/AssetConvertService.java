package io.talken.dex.api.service;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.exception.AssetConvertException;
import io.talken.dex.exception.TokenMetaDataNotFoundException;
import io.talken.dex.exception.TokenMetaLoadException;
import io.talken.dex.util.StellarConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Asset;

@Service
@Scope("singleton")
public class AssetConvertService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AssetConvertService.class);

	@Autowired
	private TokenMetaService tmService;

	// interchange assets, in order
	private static final String[] INTERCHANGE = new String[]{"BTC", "ETH", "XLM", "CTX"};

	public double convert(String fromCode, double amount, String toCode) throws AssetConvertException, TokenMetaDataNotFoundException, TokenMetaLoadException {
		return StellarConverter.rawToDouble(convertRaw(fromCode, StellarConverter.doubleToRaw(amount), toCode));
	}

	public long convertRaw(String fromCode, long amountRaw, String toCode) throws AssetConvertException, TokenMetaDataNotFoundException, TokenMetaLoadException {
		return convertRaw(tmService.getAssetType(fromCode), amountRaw, tmService.getAssetType(toCode));
	}

	public long convertRaw(Asset fromType, long amountRaw, Asset toType) throws AssetConvertException, TokenMetaDataNotFoundException, TokenMetaLoadException {
		tmService.checkTaExrAndUpdate();

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

	public double exchange(String fromCode, double amount, String toCode) throws AssetConvertException, TokenMetaDataNotFoundException, TokenMetaLoadException {
		return StellarConverter.rawToDouble(exchangeRawToFiat(fromCode, StellarConverter.doubleToRaw(amount), toCode));
	}

	public long exchangeRawToFiat(String fromCode, long amountRaw, String toCode) throws AssetConvertException, TokenMetaDataNotFoundException, TokenMetaLoadException {
		tmService.checkTaExrAndUpdate();

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
		TokenMetaData baseMeta = tmService.getTokenMeta(base);
		if(baseMeta.getManagedInfo() == null) return null;
		if(baseMeta.getManagedInfo().getMarketPair() == null) return null;
		if(baseMeta.getManagedInfo().getMarketPair().get(counter) == null) return null;
		return baseMeta.getManagedInfo().getMarketPair().get(counter).getPriceC();
	}

	private Double getExchangeRate(String base, String counter) throws TokenMetaDataNotFoundException {
		TokenMetaData baseMeta = tmService.getTokenMeta(base);
		if(baseMeta.getExchangeRate() == null) return null;
		return baseMeta.getExchangeRate().get(counter);
	}
}
