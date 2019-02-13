package io.colligence.talken.dex.service;

import io.colligence.talken.common.CommonConsts;
import io.colligence.talken.common.persistence.redis.AssetExchangeRate;
import io.colligence.talken.common.persistence.redis.AssetOHLCData;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.api.mas.ma.ManagedAccountService;
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

	public double convertAsset(String fromCode, double amount, String toCode) throws AssetConvertException, AssetTypeNotFoundException {
		return convertAsset(maService.getAssetType(fromCode), amount, maService.getAssetType(toCode));
	}

	public double convertAsset(Asset fromType, double amount, Asset toType) throws AssetConvertException {
		checkRedisData();

		final String from = StellarConverter.toAssetCode(fromType);
		final String to = StellarConverter.toAssetCode(toType);

		// first look up TradeAggregation data
		Double rate = getClosePrice(from, to);

		if(rate != null) {
			return amount * rate;
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
			return amount * rate;
		}

		// fallback to CoinMarketCap data
		rate = getExchangeRate(from, to);
		if(rate != null) {
			return amount * rate;
		}

		throw new AssetConvertException(from, to);
	}

	public double exchangeAssetToFiat(String from, double amount, String to) throws AssetConvertException {
		checkRedisData();

		// exchange to fiat
		if(!to.equalsIgnoreCase("USD") && !to.equalsIgnoreCase("KRW")) {
			throw new AssetConvertException(from, to);
		}

		Double rate = getExchangeRate(from, to);
		if(rate != null) {
			return amount * rate;
		}

		// try interchange with trade aggregation data
		// ex: MOBI -> BTC -> KRW
		for(String ic : INTERCHANGE) {
			if(!ic.equals(from)) {
				Double ic_rate = getClosePrice(from, ic);
				if(ic_rate != null) {
					Double ic_rate2 = getExchangeRate(ic, to);
					if(ic_rate2 != null) {
						rate = ic_rate * ic_rate2;
						break;
					}
				}
			}
		}

		if(rate != null) {
			return amount * rate;
		}

		throw new AssetConvertException(from, to);
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
			Optional<Record1<Double>> opt_price = dslContext
					.select(ITGR_CMC_QUOTE.PRICE)
					.from(ITGR_CMC
							.leftOuterJoin(ITGR_CMC).on(ITGR_CMC.ID.eq(ITGR_CMC_QUOTE.ITGR_CMC_ID))
					)
					.where(ITGR_CMC.SYMBOL.eq(base).and(ITGR_CMC_QUOTE.CURRENCY.eq(counter)))
					.fetchOptional();

			if(opt_price.isPresent())
				return opt_price.get().get(ITGR_CMC_QUOTE.PRICE);
			else
				return null;
		}
	}
}
