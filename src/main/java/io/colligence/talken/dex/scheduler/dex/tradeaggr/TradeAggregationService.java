package io.colligence.talken.dex.scheduler.dex.tradeaggr;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.common.util.UTCUtil;
import io.colligence.talken.dex.api.mas.ma.ManagedAccountService;
import io.colligence.talken.dex.exception.AssetTypeNotFoundException;
import io.colligence.talken.dex.scheduler.dex.txmonitor.TaskTransactionMonitor;
import io.colligence.talken.dex.service.integration.stellar.StellarNetworkService;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Asset;
import org.stellar.sdk.Server;
import org.stellar.sdk.responses.Page;
import org.stellar.sdk.responses.TradeAggregationResponse;

import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantLock;

import static io.colligence.talken.common.CommonConsts.ZONE_UTC;
import static io.colligence.talken.common.persistence.jooq.Tables.TOKEN_MARKET_PAIR;
import static io.colligence.talken.common.persistence.jooq.Tables.TOKEN_META;

@Service
@Scope("singleton")
public class TradeAggregationService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TaskTransactionMonitor.class);

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private ManagedAccountService maService;

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
						int updated = dslContext.update(TOKEN_MARKET_PAIR)
								.set(TOKEN_MARKET_PAIR.AGGR_TIMESTAMP, UTCUtil.ts2ldt(aggr.getTimestamp() / 1000))
								.set(TOKEN_MARKET_PAIR.BASE_VOLUME, Double.valueOf(aggr.getBaseVolume()))
								.set(TOKEN_MARKET_PAIR.COUNTER_VOLUME, Double.valueOf(aggr.getCounterVolume()))
								.set(TOKEN_MARKET_PAIR.TRADE_COUNT, aggr.getTradeCount())
								.set(TOKEN_MARKET_PAIR.PRICE_AVG, Double.valueOf(aggr.getAvg()))
								.set(TOKEN_MARKET_PAIR.PRICE_O, Double.valueOf(aggr.getOpen()))
								.set(TOKEN_MARKET_PAIR.PRICE_H, Double.valueOf(aggr.getHigh()))
								.set(TOKEN_MARKET_PAIR.PRICE_L, Double.valueOf(aggr.getLow()))
								.set(TOKEN_MARKET_PAIR.PRICE_C, Double.valueOf(aggr.getClose()))
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
	}
}
