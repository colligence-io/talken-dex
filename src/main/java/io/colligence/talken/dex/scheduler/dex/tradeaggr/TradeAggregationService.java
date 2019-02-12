package io.colligence.talken.dex.scheduler.dex.tradeaggr;

import io.colligence.talken.common.persistence.redis.AssetOHLCData;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.common.util.UTCUtil;
import io.colligence.talken.dex.api.mas.ma.ManagedAccountService;
import io.colligence.talken.dex.exception.AssetTypeNotFoundException;
import io.colligence.talken.dex.service.integration.stellar.StellarNetworkService;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Asset;
import org.stellar.sdk.Server;
import org.stellar.sdk.responses.Page;
import org.stellar.sdk.responses.TradeAggregationResponse;

import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantLock;

import static io.colligence.talken.common.CommonConsts.REDIS.KEY_ASSET_OHLCV;
import static io.colligence.talken.common.CommonConsts.REDIS.KEY_ASSET_OHLCV_UPDATED;
import static io.colligence.talken.common.CommonConsts.ZONE_UTC;
import static io.colligence.talken.common.persistence.jooq.Tables.TOKEN_MARKET_PAIR;
import static io.colligence.talken.common.persistence.jooq.Tables.TOKEN_META;

@Service
@Scope("singleton")
public class TradeAggregationService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TradeAggregationService.class);

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private ManagedAccountService maService;

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	@Autowired
	private DSLContext dslContext;

	private ReentrantLock lock = new ReentrantLock();

	@Scheduled(cron = "0 */10 * * * *", zone = ZONE_UTC)
	private void do_schedule() {
		if(!lock.isLocked()) {
			try {
				lock.lock();
				LocalDateTime endTime = UTCUtil.getNow();
				LocalDateTime startTime = endTime.minusDays(1);
				logger.info("Gather trade aggregations data for {} ~ {}", startTime, endTime);
				aggregate(UTCUtil.toTimestamp_s(startTime) * 1000, UTCUtil.toTimestamp_s(endTime) * 1000);
			} catch(Exception ex) {
				logger.exception(ex);
			} finally {
				lock.unlock();
			}
		}
	}

	private void aggregate(long startTime, long endTime) {
		io.colligence.talken.common.persistence.jooq.tables.TOKEN_META base = TOKEN_META.as("base");
		io.colligence.talken.common.persistence.jooq.tables.TOKEN_META counter = TOKEN_META.as("counter");

		Result<Record> mpList = dslContext
				.selectFrom(TOKEN_MARKET_PAIR
						.leftOuterJoin(base).on(base.ID.eq(TOKEN_MARKET_PAIR.TOKEN_META_ID))
						.leftOuterJoin(counter).on(counter.ID.eq(TOKEN_MARKET_PAIR.COUNTER_META_ID))
				)
				.where(base.MANAGED_FLAG.eq(true).and(counter.MANAGED_FLAG.eq(true)).and(TOKEN_MARKET_PAIR.ACTIVE_FLAG.eq(true)))
				.fetch();

		AssetOHLCData redisData = new AssetOHLCData();

		for(Record _mpRecord : mpList) {
			try {
				String baseAsset = _mpRecord.get(base.SYMBOL);
				String counterAsset = _mpRecord.get(counter.SYMBOL);
				Asset baseAssetType = maService.getAssetType(baseAsset);
				Asset counterAssetType = maService.getAssetType(counterAsset);


				if(!redisData.containsKey(baseAsset))
					redisData.put(baseAsset, new AssetOHLCData.Base());
				AssetOHLCData.Base redisBase = redisData.get(baseAsset);

				TradeAggregationResponse aggr = null;
				try {
					Server server = stellarNetworkService.pickServer();
					logger.debug("execute trade aggregation for {}/{} {}~{}", counterAsset, baseAsset, startTime, endTime);
					Page<TradeAggregationResponse> aggrPage = server.tradeAggregations(baseAssetType, counterAssetType, startTime, endTime, 86400000, 0).execute();
					if(aggrPage.getRecords().size() > 0)
						aggr = aggrPage.getRecords().get(0);
				} catch(Exception ex) {
					logger.exception(ex, "Trade aggregation execution error");
				}

				if(aggr != null) {
					try {
						AssetOHLCData.Counter redisCounter = new AssetOHLCData.Counter();
						redisBase.put(counterAsset, redisCounter);

						redisCounter.setTimestamp(aggr.getTimestamp() / 1000);
						redisCounter.setTradeCount(aggr.getTradeCount());
						redisCounter.setBase_volume(Double.valueOf(aggr.getBaseVolume()));
						redisCounter.setCounter_volume(Double.valueOf(aggr.getCounterVolume()));
						redisCounter.setPrice_avg(Double.valueOf(aggr.getAvg()));
						redisCounter.setPrice_o(Double.valueOf(aggr.getOpen()));
						redisCounter.setPrice_h(Double.valueOf(aggr.getHigh()));
						redisCounter.setPrice_l(Double.valueOf(aggr.getLow()));
						redisCounter.setPrice_c(Double.valueOf(aggr.getClose()));

						int updated = dslContext.update(TOKEN_MARKET_PAIR)
								.set(TOKEN_MARKET_PAIR.AGGR_TIMESTAMP, UTCUtil.ts2ldt(redisCounter.getTimestamp()))
								.set(TOKEN_MARKET_PAIR.BASE_VOLUME, redisCounter.getBase_volume())
								.set(TOKEN_MARKET_PAIR.COUNTER_VOLUME, redisCounter.getCounter_volume())
								.set(TOKEN_MARKET_PAIR.TRADE_COUNT, redisCounter.getTradeCount())
								.set(TOKEN_MARKET_PAIR.PRICE_AVG, redisCounter.getPrice_avg())
								.set(TOKEN_MARKET_PAIR.PRICE_O, redisCounter.getPrice_o())
								.set(TOKEN_MARKET_PAIR.PRICE_H, redisCounter.getPrice_h())
								.set(TOKEN_MARKET_PAIR.PRICE_L, redisCounter.getPrice_l())
								.set(TOKEN_MARKET_PAIR.PRICE_C, redisCounter.getPrice_c())
								.where(TOKEN_MARKET_PAIR.ID.eq(_mpRecord.get(TOKEN_MARKET_PAIR.ID)))
								.execute();
						if(updated > 0)
							logger.debug("{}/{} market pair data updated", counterAsset, baseAsset);
						else
							logger.error("active {}/{} market pair not found", counterAsset, baseAsset);

					} catch(Exception ex) {
						logger.exception(ex, "Aggregation processing error");
					}
				} else {
					logger.debug("No trade aggregation data for {}/{} {}~{}", counterAsset, baseAsset, startTime, endTime);
				}

			} catch(AssetTypeNotFoundException ex) {
				logger.exception(ex, "Asset {} not found on managed account service.", ex.getAssetCode());
			}
		}

		try {
			redisTemplate.opsForValue().set(KEY_ASSET_OHLCV_UPDATED, UTCUtil.getNowTimestamp_s());
			redisTemplate.opsForValue().set(KEY_ASSET_OHLCV, redisData);
		} catch(Exception ex) {
			logger.exception(ex, "Cannot update redis {}", KEY_ASSET_OHLCV);
		}
	}
}
