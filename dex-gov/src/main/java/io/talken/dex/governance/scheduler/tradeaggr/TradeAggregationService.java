package io.talken.dex.governance.scheduler.tradeaggr;

import io.talken.common.persistence.jooq.tables.TOKEN_META;
import io.talken.common.persistence.redis.AssetOHLCData;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.shared.exception.TokenMetaDataNotFoundException;
import io.talken.dex.shared.service.StellarNetworkService;
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

@Service
@Scope("singleton")
public class TradeAggregationService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TradeAggregationService.class);

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private TokenMetaGovService maService;

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
				.selectFrom(TOKEN_META_MANAGED_MARKETPAIR
						.leftOuterJoin(base).on(base.ID.eq(TOKEN_META_MANAGED_MARKETPAIR.TM_ID))
						.leftOuterJoin(counter).on(counter.ID.eq(TOKEN_META_MANAGED_MARKETPAIR.TM_ID_COUNTER))
				)
				.where(TOKEN_META_MANAGED_MARKETPAIR.ACTIVE_FLAG.eq(true))
				.fetch();

		AssetOHLCData ohlcData = new AssetOHLCData();

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

					AssetOHLCData.Counter olhc = ohlcData.ofBase(baseAsset).ofCounter(counterAsset);

					try {
						int updated = dslContext.update(TOKEN_META_MANAGED_MARKETPAIR)
								.set(TOKEN_META_MANAGED_MARKETPAIR.AGGR_TIMESTAMP, UTCUtil.ts2ldt(aggr.getTimestamp() / 1000))
								.set(TOKEN_META_MANAGED_MARKETPAIR.BASE_VOLUME, Double.valueOf(aggr.getBaseVolume()))
								.set(TOKEN_META_MANAGED_MARKETPAIR.COUNTER_VOLUME, Double.valueOf(aggr.getCounterVolume()))
								.set(TOKEN_META_MANAGED_MARKETPAIR.TRADE_COUNT, aggr.getTradeCount())
								.set(TOKEN_META_MANAGED_MARKETPAIR.PRICE_AVG, Double.valueOf(aggr.getAvg()))
								.set(TOKEN_META_MANAGED_MARKETPAIR.PRICE_O, Double.valueOf(aggr.getOpen()))
								.set(TOKEN_META_MANAGED_MARKETPAIR.PRICE_H, Double.valueOf(aggr.getHigh()))
								.set(TOKEN_META_MANAGED_MARKETPAIR.PRICE_L, Double.valueOf(aggr.getLow()))
								.set(TOKEN_META_MANAGED_MARKETPAIR.PRICE_C, Double.valueOf(aggr.getClose()))
								.where(TOKEN_META_MANAGED_MARKETPAIR.ID.eq(_mpRecord.get(TOKEN_META_MANAGED_MARKETPAIR.ID)))
								.execute();

						olhc.setBase_volume(Double.valueOf(aggr.getBaseVolume()));
						olhc.setCounter_volume(Double.valueOf(aggr.getCounterVolume()));
						olhc.setTradeCount(aggr.getTradeCount());
						olhc.setPrice_avg(Double.valueOf(aggr.getAvg()));
						olhc.setPrice_h(Double.valueOf(aggr.getHigh()));
						olhc.setPrice_l(Double.valueOf(aggr.getLow()));
						olhc.setPrice_o(Double.valueOf(aggr.getOpen()));
						olhc.setPrice_c(Double.valueOf(aggr.getClose()));
						olhc.setTimestamp(aggr.getTimestamp() / 1000);

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
			redisTemplate.opsForValue().set(AssetOHLCData.REDIS_KEY, ohlcData);
			redisTemplate.opsForValue().set(AssetOHLCData.REDIS_UPDATED_KEY, UTCUtil.getNowTimestamp_s());
		} catch(Exception ex) {
			logger.exception(ex, "Cannot update redis");
		}

		try {
			dslContext.update(DEX_GOV_STATUS).set(DEX_GOV_STATUS.TRADEAGGRLASTTIMESTAMP, endTimeLdt).execute();
		} catch(Exception ex) {
			logger.exception(ex, "Cannot update dex_status.tradeAggrLastTimestamp", ex.getMessage());
		}
	}
}
