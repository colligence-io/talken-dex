package io.talken.dex.governance.scheduler.tradeaggr;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.exception.common.TokenMetaNotManagedException;
import io.talken.common.persistence.jooq.tables.TOKEN_META;
import io.talken.common.persistence.redis.AssetOHLCData;
import io.talken.common.service.ServiceStatusService;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import lombok.Data;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantLock;

import static io.talken.common.CommonConsts.ZONE_UTC;
import static io.talken.common.persistence.jooq.Tables.TOKEN_META;
import static io.talken.common.persistence.jooq.Tables.TOKEN_META_MANAGED_MARKETPAIR;

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

	@Autowired
	private ServiceStatusService ssService;

	private ReentrantLock lock = new ReentrantLock();

	@Data
	public static class TradeAggregatorStatus {
		private LocalDateTime lastAggregation;
	}

	@Scheduled(cron = "0 */10 * * * *", zone = ZONE_UTC)
	private void do_schedule() {
		if(DexGovStatus.isStopped) return;

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

	/**
	 * aggregate trade OHLCV
	 *
	 * @param startTimeLdt
	 * @param endTimeLdt
	 */
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
				Asset baseAssetType = maService.getManagedInfo(baseAsset).dexAssetType();
				Asset counterAssetType = maService.getManagedInfo(counterAsset).dexAssetType();

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
								.set(TOKEN_META_MANAGED_MARKETPAIR.BASE_VOLUME, new BigDecimal(aggr.getBaseVolume()))
								.set(TOKEN_META_MANAGED_MARKETPAIR.COUNTER_VOLUME, new BigDecimal(aggr.getCounterVolume()))
								.set(TOKEN_META_MANAGED_MARKETPAIR.TRADE_COUNT, aggr.getTradeCount())
								.set(TOKEN_META_MANAGED_MARKETPAIR.PRICE_AVG, new BigDecimal(aggr.getAvg()))
								.set(TOKEN_META_MANAGED_MARKETPAIR.PRICE_O, new BigDecimal(aggr.getOpen()))
								.set(TOKEN_META_MANAGED_MARKETPAIR.PRICE_H, new BigDecimal(aggr.getHigh()))
								.set(TOKEN_META_MANAGED_MARKETPAIR.PRICE_L, new BigDecimal(aggr.getLow()))
								.set(TOKEN_META_MANAGED_MARKETPAIR.PRICE_C, new BigDecimal(aggr.getClose()))
								.where(TOKEN_META_MANAGED_MARKETPAIR.ID.eq(_mpRecord.get(TOKEN_META_MANAGED_MARKETPAIR.ID)))
								.execute();

						olhc.setBase_volume(new BigDecimal(aggr.getBaseVolume()));
						olhc.setCounter_volume(new BigDecimal(aggr.getCounterVolume()));
						olhc.setTradeCount(aggr.getTradeCount());
						olhc.setPrice_avg(new BigDecimal(aggr.getAvg()));
						olhc.setPrice_h(new BigDecimal(aggr.getHigh()));
						olhc.setPrice_l(new BigDecimal(aggr.getLow()));
						olhc.setPrice_o(new BigDecimal(aggr.getOpen()));
						olhc.setPrice_c(new BigDecimal(aggr.getClose()));
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

			} catch(TokenMetaNotFoundException | TokenMetaNotManagedException ex) {
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
			ssService.of(TradeAggregatorStatus.class).update((s) -> {
				s.setLastAggregation(endTimeLdt);
			});
		} catch(Exception ex) {
			logger.exception(ex, "Cannot update dex_status.tradeAggrLastTimestamp", ex.getMessage());
		}
	}
}
