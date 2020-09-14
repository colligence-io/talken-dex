package io.talken.dex.governance.scheduler.crawler.cmc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import io.talken.common.RunningProfile;
import io.talken.common.persistence.jooq.tables.records.TokenMetaRecord;
import io.talken.common.persistence.mongodb.cmc.CMCLatestData;
import io.talken.common.persistence.mongodb.cmc.CMCLatestEntryData;
import io.talken.common.persistence.mongodb.cmc.CMCQuoteData;
import io.talken.common.persistence.redis.AssetExchangeRate;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.GovSettings;
import io.talken.dex.shared.TransactionBlockExecutor;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicLong;

import static io.talken.common.CommonConsts.ZONE_UTC;
import static io.talken.common.persistence.jooq.Tables.*;

@Service
@Scope("singleton")
public class CrawlCmcCryptoCurrencyService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(CrawlCmcCryptoCurrencyService.class);

	private static HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();

	@Autowired
	private GovSettings govSettings;

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private AdminAlarmService alarmService;

	@Autowired
	private DataSourceTransactionManager txMgr;

    private static final String COLLECTION_NAME = "cmc";
    private static final String API_NAME = "cryptocurrency";

	private static AtomicLong counter = new AtomicLong(0);

	public static class CoinMarketCapLatestResult extends CoinMarketCapResult<CMCLatestData> {}

    @PostConstruct
    private void init() throws Exception {
        if (mongoTemplate.getCollection(COLLECTION_NAME).countDocuments() == 0) {
            mongoTemplate.createCollection(COLLECTION_NAME);
        }
    }

	@Scheduled(cron = "0 */6 * * * *", zone = ZONE_UTC)
	private void crawl() {
		if(DexGovStatus.isStopped) return;
        logger.debug("CMC CrawlerService ["+API_NAME+"] started at : {}", UTCUtil.getNow());
		counter.incrementAndGet();
		try {
//            crawlCMCLatest();
			if(RunningProfile.isProduction()) {
				crawlCMCLatest();
			} else { // for saving CMC credit, run every 4 hours only when it's not production environment
				if(counter.get() % 24 == 0) {
					crawlCMCLatest();
				} else {
					logger.debug("Skip CMC crawler task for saving credit.");
				}
			}
		} catch(Exception ex) {
			logger.exception(ex);
		}
	}

	/**
	 * Crawl CMC latest info
	 * @throws Exception
	 */
	protected void crawlCMCLatest() throws Exception {
		GenericUrl url = new GenericUrl(govSettings.getIntegration().getCoinMarketCap().getCryptoCurrencyUrl());

		Result<TokenMetaRecord> tmList = dslContext.selectFrom(TOKEN_META)
				.where(TOKEN_META.CMC_ID.isNotNull())
				.fetch();
		Map<Integer, Long> tokenMetaIdMap = new HashMap<>();

		StringJoiner sj = new StringJoiner(",");
		for(TokenMetaRecord _tm : tmList) {
			sj.add(Integer.toString(_tm.getCmcId()));
			tokenMetaIdMap.put(_tm.getCmcId(), _tm.getId());
		}

		url.set("id", sj.toString());
		url.set("convert", "BTC,ETH,XLM,USDT,USD,KRW");

		HttpHeaders headers = new HttpHeaders();
		headers.set("X-CMC_PRO_API_KEY", govSettings.getIntegration().getCoinMarketCap().getApiKey());

		logger.debug("Request CoinMarketCap latest data API.");
		HttpResponse response = requestFactory.buildGetRequest(url).setHeaders(headers).execute();

		if(response.getStatusCode() != 200) {
			alarmService.error(logger, "CoinMarketCap API call error : {} - {}", response.getStatusCode(), response.getStatusMessage());
			return;
		}

		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		CoinMarketCapLatestResult cmcr = mapper.readValue(response.parseAsString(), CoinMarketCapLatestResult.class);

		if(cmcr.getStatus().getError_code() != 0) {
			alarmService.error(logger, "CoinMarketCap API error : {} - {}", cmcr.getStatus().getError_code(), cmcr.getStatus().getError_message());
			return;
		}

		TransactionBlockExecutor.of(txMgr).transactional(() -> {
			long updated = 0;

			if(cmcr.getData() != null) {
				AssetExchangeRate exc_rate = new AssetExchangeRate();

				for(CMCLatestEntryData _cc : cmcr.getData().values()) {
					String tags = null;
					if(_cc.getTags() != null)
						tags = String.join(",", _cc.getTags());

					Integer platform_id = null;
					String platform_token_address = null;

					if(_cc.getPlatform() != null) {
						platform_id = _cc.getPlatform().getId();
						platform_token_address = _cc.getPlatform().getToken_address();
					}

					dslContext.insertInto(CRAWL_CMC,
							CRAWL_CMC.ID,
							CRAWL_CMC.NAME,
							CRAWL_CMC.SYMBOL,
							CRAWL_CMC.SLUG,
							CRAWL_CMC.CMC_RANK,
							CRAWL_CMC.NUM_MARKET_PAIRS,
							CRAWL_CMC.CIRCULATING_SUPPLY,
							CRAWL_CMC.TOTAL_SUPPLY,
							CRAWL_CMC.MAX_SUPPLY,
							CRAWL_CMC.LAST_UPDATED,
							CRAWL_CMC.DATE_ADDED,
							CRAWL_CMC.TAGS,
							CRAWL_CMC.PLATFORM_ID,
							CRAWL_CMC.PLATFORM_TOKEN_ADDRESS)
							.values(
									(long) _cc.getId(),
									_cc.getName(),
									_cc.getSymbol(),
									_cc.getSlug(),
									_cc.getCmc_rank(),
									_cc.getNum_market_pairs(),
									_cc.getCirculating_supply(),
									_cc.getTotal_supply(),
									_cc.getMax_supply(),
									_cc.getLast_updated(),
									_cc.getDate_added(),
									tags,
									platform_id,
									platform_token_address
							)
							.onDuplicateKeyUpdate()
							.set(CRAWL_CMC.NAME, _cc.getName())
							.set(CRAWL_CMC.SYMBOL, _cc.getSymbol())
							.set(CRAWL_CMC.SLUG, _cc.getSlug())
							.set(CRAWL_CMC.CMC_RANK, _cc.getCmc_rank())
							.set(CRAWL_CMC.NUM_MARKET_PAIRS, _cc.getNum_market_pairs())
							.set(CRAWL_CMC.CIRCULATING_SUPPLY, _cc.getCirculating_supply())
							.set(CRAWL_CMC.TOTAL_SUPPLY, _cc.getTotal_supply())
							.set(CRAWL_CMC.MAX_SUPPLY, _cc.getMax_supply())
							.set(CRAWL_CMC.LAST_UPDATED, _cc.getLast_updated())
							.set(CRAWL_CMC.DATE_ADDED, _cc.getDate_added())
							.set(CRAWL_CMC.TAGS, tags)
							.set(CRAWL_CMC.PLATFORM_ID, platform_id)
							.set(CRAWL_CMC.PLATFORM_TOKEN_ADDRESS, platform_token_address)
							.execute();

					if(_cc.getQuote() != null) {
						for(Map.Entry<String, CMCQuoteData> _qkv : _cc.getQuote().entrySet()) {
							String quote = _qkv.getKey().toUpperCase();
							CMCQuoteData cqd = _qkv.getValue();

							// TODO :add cqd.getVolume_24h();

							dslContext.insertInto(CRAWL_CMC_QUOTE,
									CRAWL_CMC_QUOTE.ID,
									CRAWL_CMC_QUOTE.CURRENCY,
									CRAWL_CMC_QUOTE.PRICE,
									CRAWL_CMC_QUOTE.MARKET_CAP,
                                    CRAWL_CMC_QUOTE.VOLUME_24H,
									CRAWL_CMC_QUOTE.PERCENT_CHANGE_1H,
									CRAWL_CMC_QUOTE.PERCENT_CHANGE_24H,
									CRAWL_CMC_QUOTE.PERCENT_CHANGE_7D,
									CRAWL_CMC_QUOTE.LAST_UPDATED)
									.values(
											(long) _cc.getId(),
											quote,
											cqd.getPrice(),
											cqd.getMarket_cap(),
											cqd.getVolume_24h(),
											cqd.getPercent_change_1h(),
											cqd.getPercent_change_24h(),
											cqd.getPercent_change_7d(),
											cqd.getLast_updated()
									)
									.onDuplicateKeyUpdate()
									.set(CRAWL_CMC_QUOTE.PRICE, cqd.getPrice())
									.set(CRAWL_CMC_QUOTE.MARKET_CAP, cqd.getMarket_cap())
                                    .set(CRAWL_CMC_QUOTE.VOLUME_24H, cqd.getVolume_24h())
									.set(CRAWL_CMC_QUOTE.PERCENT_CHANGE_1H, cqd.getPercent_change_1h())
									.set(CRAWL_CMC_QUOTE.PERCENT_CHANGE_24H, cqd.getPercent_change_24h())
									.set(CRAWL_CMC_QUOTE.PERCENT_CHANGE_7D, cqd.getPercent_change_7d())
									.set(CRAWL_CMC_QUOTE.LAST_UPDATED, cqd.getLast_updated())
									.execute();

							exc_rate.ofBase(_cc.getSymbol()).addRate(quote, cqd.getPrice(), UTCUtil.toTimestamp_s(cqd.getLast_updated()));

							// update token_exchange_rate
							if(tokenMetaIdMap.containsKey(_cc.getId())) {
								dslContext.insertInto(TOKEN_META_EXRATE, TOKEN_META_EXRATE.TM_ID, TOKEN_META_EXRATE.COUNTERTYPE, TOKEN_META_EXRATE.PRICE)
										.values(tokenMetaIdMap.get(_cc.getId()), quote.toUpperCase(), cqd.getPrice())
										.onDuplicateKeyUpdate()
										.set(TOKEN_META_EXRATE.PRICE, cqd.getPrice())
										.execute();
							}
						}
					}

					updated++;
				}

				// store history in mongodb
				try {
					mongoTemplate.save(cmcr.getData(), COLLECTION_NAME + "_" + API_NAME);
				} catch(Exception ex) {
					logger.exception(ex);
				}

				// publish exchange rate data to redis
				try {
					redisTemplate.opsForValue().set(AssetExchangeRate.REDIS_UPDATED_KEY, UTCUtil.getNowTimestamp_s());
					redisTemplate.opsForValue().set(AssetExchangeRate.REDIS_KEY, exc_rate);
				} catch(Exception ex) {
					logger.exception(ex);
				}
			}

			logger.info("Crawled {} data from coinmarketcap. {} credits used.", updated, cmcr.getStatus().getCredit_count());
		});
	}
}
