package io.colligence.talken.dex.api.service;

import io.colligence.talken.common.CommonConsts;
import io.colligence.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.colligence.talken.common.persistence.redis.AssetExchangeRate;
import io.colligence.talken.common.persistence.redis.AssetOHLCData;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.exception.AssetConvertException;
import io.colligence.talken.dex.exception.AssetTypeNotFoundException;
import io.colligence.talken.dex.util.StellarConverter;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Asset;

import javax.annotation.PostConstruct;
import java.util.Optional;

import static io.colligence.talken.common.persistence.jooq.Tables.*;

@Service
@Scope("singleton")
public class AssetConvertService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AssetConvertService.class);

	@Autowired
	private ManagedAccountService maService;

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	private Long taDataUpdated;
	private AssetOHLCData taData;
	private Long excDataUpdated;
	private AssetExchangeRate excData;

	// interchange assets, in order
	private static final String[] INTERCHANGE = new String[]{"BTC", "ETH", "XLM", "CTX"};

	@PostConstruct
	private void init() {
		checkRedisData();
	}


	public double convert(String fromCode, double amount, String toCode) throws AssetConvertException, AssetTypeNotFoundException {
		return StellarConverter.rawToDouble(convertRaw(fromCode, StellarConverter.doubleToRaw(amount), toCode));
	}

	public long convertRaw(String fromCode, long amountRaw, String toCode) throws AssetConvertException, AssetTypeNotFoundException {
		return convertRaw(maService.getAssetType(fromCode), amountRaw, maService.getAssetType(toCode));
	}

	public long convertRaw(Asset fromType, long amountRaw, Asset toType) throws AssetConvertException {
		checkRedisData();


		final String from = StellarConverter.toAssetCode(fromType);
		final String to = StellarConverter.toAssetCode(toType);

		// first look up TradeAggregation data
		Double rate = getClosePrice(from, to);

		if(rate != null) {
			return (long) (amountRaw * rate);
		}

		// try interchange
		if(taData != null) {
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

	public double exchange(String fromCode, double amount, String toCode) throws AssetConvertException {
		return StellarConverter.rawToDouble(exchangeRawToFiat(fromCode, StellarConverter.doubleToRaw(amount), toCode));
	}

	public long exchangeRawToFiat(String fromCode, long amountRaw, String toCode) throws AssetConvertException {
		checkRedisData();

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

	private void checkRedisData() {
		Long taDataRedisUpdated = null;
		try {
			Object val = redisTemplate.opsForValue().get(CommonConsts.REDIS.KEY_ASSET_OHLCV_UPDATED);
			if(val != null)
				taDataRedisUpdated = Long.valueOf(val.toString());
		} catch(Exception ex) {
			logger.exception(ex, "Cannot get data {} from redis", CommonConsts.REDIS.KEY_ASSET_OHLCV_UPDATED);
		}

		if(taDataRedisUpdated != null) {
			if(taDataUpdated == null || taDataUpdated < taDataRedisUpdated) {
				try {
					taData = (AssetOHLCData) redisTemplate.opsForValue().get(CommonConsts.REDIS.KEY_ASSET_OHLCV);
					taDataUpdated = taDataRedisUpdated;
				} catch(Exception ex) {
					logger.exception(ex, "Cannot get data {} from redis", CommonConsts.REDIS.KEY_ASSET_OHLCV);
				}
			}
		}

		Long excDataRedisUpdated = null;
		try {
			Object val = redisTemplate.opsForValue().get(CommonConsts.REDIS.KEY_ASSET_EXRATE_UPDATED);
			if(val != null)
				excDataRedisUpdated = Long.valueOf(val.toString());
		} catch(Exception ex) {
			logger.exception(ex, "Cannot get data {} from redis", CommonConsts.REDIS.KEY_ASSET_EXRATE_UPDATED);
		}

		if(excDataRedisUpdated != null) {
			if(excDataUpdated == null || excDataUpdated < excDataRedisUpdated) {
				try {
					excData = (AssetExchangeRate) redisTemplate.opsForValue().get(CommonConsts.REDIS.KEY_ASSET_EXRATE);
					excDataUpdated = excDataRedisUpdated;
				} catch(Exception ex) {
					logger.exception(ex, "Cannot get data {} from redis", CommonConsts.REDIS.KEY_ASSET_EXRATE);
				}
			}
		}
	}

	private Double getClosePrice(String base, String counter) {
		if(taData != null) {
			if(taData.containsKey(base) && taData.get(base).containsKey(counter))
				return taData.get(base).get(counter).getPrice_c();
			else return null;
		} else {
			// TODO : optimize required, this will query db every time!
			// maybe this is not required at all!
			io.colligence.talken.common.persistence.jooq.tables.TOKEN_META base_table = TOKEN_META.as("base");
			io.colligence.talken.common.persistence.jooq.tables.TOKEN_META counter_table = TOKEN_META.as("counter");

			Optional<Record1<Double>> opt_close = dslContext
					.select(TOKEN_MARKET_PAIR.PRICE_C)
					.from(TOKEN_MARKET_PAIR
							.leftOuterJoin(base_table).on(base_table.ID.eq(TOKEN_MARKET_PAIR.TOKEN_META_ID))
							.leftOuterJoin(counter_table).on(counter_table.ID.eq(TOKEN_MARKET_PAIR.COUNTER_META_ID))
					)
					.where(base_table.MANAGED_FLAG.eq(true).and(base_table.SYMBOL.eq(base)).and(counter_table.MANAGED_FLAG.eq(true)).and(counter_table.SYMBOL.eq(counter)).and(TOKEN_MARKET_PAIR.ACTIVE_FLAG.eq(true)))
					.fetchOptional();

			if(opt_close.isPresent())
				return opt_close.get().get(TOKEN_MARKET_PAIR.PRICE_C);
			else
				return null;
		}
	}

	private Double getExchangeRate(String base, String counter) {
		if(excData != null) {
			if(excData.containsKey(base) && excData.get(base).containsKey(counter))
				return excData.get(base).get(counter).getPrice();
			else return null;
		} else {
			// TODO : optimize required, this will query db every time!
			// maybe this is not required at all!
			TokenMetaAuxCodeEnum metaAux;
			try {
				metaAux = TokenMetaAuxCodeEnum.getCmcPriceDataTypeFromSymbol(counter);
			} catch(IllegalArgumentException ex) {
				logger.exception(ex);
				return null;
			}

			Optional<Record1<Double>> opt_price = dslContext
					.select(TOKEN_META_AUX.DATA_D)
					.from(TOKEN_META_AUX
							.leftJoin(TOKEN_META).on(TOKEN_META_AUX.TOKEN_META_ID.eq(TOKEN_META.ID))
					).where(TOKEN_META.SYMBOL.eq(base).and(TOKEN_META_AUX.AUX_CODE.eq(metaAux)))
					.fetchOptional();

//			Optional<Record1<Double>> opt_price = dslContext
//					.select(ITGR_CMC_QUOTE.PRICE)
//					.from(ITGR_CMC_QUOTE
//							.leftOuterJoin(ITGR_CMC).on(ITGR_CMC.ID.eq(ITGR_CMC_QUOTE.ITGR_CMC_ID))
//					)
//					.where(ITGR_CMC.SYMBOL.eq(base).and(ITGR_CMC_QUOTE.CURRENCY.eq(counter)))
//					.fetchOptional();

			if(opt_price.isPresent())
				return opt_price.get().get(TOKEN_META_AUX.DATA_D);
			else
				return null;
		}
	}
}
