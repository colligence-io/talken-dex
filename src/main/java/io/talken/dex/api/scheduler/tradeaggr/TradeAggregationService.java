package io.talken.dex.api.scheduler.tradeaggr;

import io.talken.common.persistence.jooq.tables.TOKEN_META;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.dex.api.service.TokenMetaService;
import io.talken.dex.api.exception.TokenMetaDataNotFoundException;
import io.talken.dex.api.service.integration.stellar.StellarNetworkService;
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

import static io.talken.common.CommonConsts.ZONE_UTC;
import static io.talken.common.persistence.jooq.Tables.*;
import static io.talken.common.persistence.redis.RedisConsts.KEY_ASSET_OHLCV_UPDATED;

@Service
@Scope("singleton")
public class TradeAggregationService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TradeAggregationService.class);

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private TokenMetaService maService;

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
				aggregate(startTime, endTime);
			} catch(Exception ex) {
				logger.exception(ex);
			} finally {
				lock.unlock();
			}
		}
	}

	private void aggregate(LocalDateTime startTimeLdt, LocalDateTime endTimeLdt) {
		long startTime = UTCUtil.toTimestamp_s(startTimeLdt) * 1000;
		long endTime = UTCUtil.toTimestamp_s(endTimeLdt) * 1000;

		TOKEN_META base = TOKEN_META.as("base");
		TOKEN_META counter = TOKEN_META.as("counter");

		Result<Record> mpList = dslContext
				.selectFrom(TOKEN_MANAGED_MARKET_PAIR
						.leftOuterJoin(base).on(base.ID.eq(TOKEN_MANAGED_MARKET_PAIR.TOKEN_META_ID))
						.leftOuterJoin(counter).on(counter.ID.eq(TOKEN_MANAGED_MARKET_PAIR.COUNTER_META_ID))
				)
				.where(TOKEN_MANAGED_MARKET_PAIR.ACTIVE_FLAG.eq(true))
				.fetch();

		for(Record _mpRecord : mpList) {
			try {
				String baseAsset = _mpRecord.get(base.SYMBOL);
				String counterAsset = _mpRecord.get(counter.SYMBOL);
				Asset baseAssetType = maService.getAssetType(baseAsset);
				Asset counterAssetType = maService.getAssetType(counterAsset);

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
						int updated = dslContext.update(TOKEN_MANAGED_MARKET_PAIR)
								.set(TOKEN_MANAGED_MARKET_PAIR.AGGR_TIMESTAMP, UTCUtil.ts2ldt(aggr.getTimestamp() / 1000))
								.set(TOKEN_MANAGED_MARKET_PAIR.BASE_VOLUME, Double.valueOf(aggr.getBaseVolume()))
								.set(TOKEN_MANAGED_MARKET_PAIR.COUNTER_VOLUME, Double.valueOf(aggr.getCounterVolume()))
								.set(TOKEN_MANAGED_MARKET_PAIR.TRADE_COUNT, aggr.getTradeCount())
								.set(TOKEN_MANAGED_MARKET_PAIR.PRICE_AVG, Double.valueOf(aggr.getAvg()))
								.set(TOKEN_MANAGED_MARKET_PAIR.PRICE_O, Double.valueOf(aggr.getOpen()))
								.set(TOKEN_MANAGED_MARKET_PAIR.PRICE_H, Double.valueOf(aggr.getHigh()))
								.set(TOKEN_MANAGED_MARKET_PAIR.PRICE_L, Double.valueOf(aggr.getLow()))
								.set(TOKEN_MANAGED_MARKET_PAIR.PRICE_C, Double.valueOf(aggr.getClose()))
								.where(TOKEN_MANAGED_MARKET_PAIR.ID.eq(_mpRecord.get(TOKEN_MANAGED_MARKET_PAIR.ID)))
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

			} catch(TokenMetaDataNotFoundException ex) {
				logger.exception(ex);
			}
		}

		try {
			redisTemplate.opsForValue().set(KEY_ASSET_OHLCV_UPDATED, UTCUtil.getNowTimestamp_s());
		} catch(Exception ex) {
			logger.exception(ex, "Cannot update redis {}", KEY_ASSET_OHLCV_UPDATED);
		}

		try {
			dslContext.update(DEX_STATUS).set(DEX_STATUS.TRADEAGGRLASTTIMESTAMP, endTimeLdt).execute();
		} catch(Exception ex) {
			logger.exception(ex, "Cannot update dex_status.tradeAggrLastTimestamp", ex.getMessage());
		}
	}
}
