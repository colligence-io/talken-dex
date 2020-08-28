package io.talken.dex.governance.scheduler.crawler.cg;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.DexGovStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static io.talken.common.CommonConsts.ZONE_UTC;

@Service
@Scope("singleton")
public class CrawlCoinGeckoService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(CrawlCoinGeckoService.class);

    private static HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();

    /**
     * CoinGecko : NOTE
     *
     * Rate Limit rule
     * 100 requests/minute
     *
     */

    private static final int RATE_LIMIT = 100;

    private static final String COLLECTION_NAME = "coin_gecko";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private AdminAlarmService alarmService;

    private static final String apiUrl = "https://api.coingecko.com/api/v3";
    private Map<String, String> apis = new HashMap<>();

    private static AtomicLong counter = new AtomicLong(0);
    private LocalDateTime lastUpdated = LocalDateTime.now();

    public CrawlCoinGeckoService() {
        this.apis.put("global", "/global");
    }

    // 10 min = 1000 * 60 * 10
//    @Scheduled(fixedDelay = 10000)
    @Scheduled(cron = "* */5 * * * *", zone = ZONE_UTC)
    private void crawl() {
        if(DexGovStatus.isStopped) return;
        logger.debug("CoinGecko CrawlerService started at : {}", UTCUtil.getNow());
        try {
            if (checkRateLimit()) {
                getMarketCapData("global");
            }
        } catch(Exception ex) {
            logger.exception(ex);
        }
    }

    /**
     * Crawl CG global api
     * @throws Exception
     */
    private void getMarketCapData(String rest) throws Exception {
        String api = apis.getOrDefault(rest, "global");

        logger.debug("Request CoinGecko marketCap data API.");
        HttpResponse response = requestFactory
                .buildGetRequest(new GenericUrl(apiUrl+api))
                .execute();

        if(response.getStatusCode() != 200) {
            alarmService.error(logger, "CoinGecko API call error : {} - {}", response.getStatusCode(), response.getStatusMessage());
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        CoinGeckoResult cgmcr = mapper.readValue(response.parseAsString(), CoinGeckoResult.class);
        LinkedHashMap currentDoc = (LinkedHashMap) cgmcr.getData();
//        logger.debug("Response CoinGecko marketCap data as ParseStream {}", cgmcr);

        MongoCollection collection = mongoTemplate.getCollection(COLLECTION_NAME);

        if (collection.countDocuments() > 0) {
            collection.deleteMany(new BasicDBObject());
        }

        lastUpdated = LocalDateTime.now();
        currentDoc.put("last_updated", lastUpdated);
        mongoTemplate.save(currentDoc, COLLECTION_NAME);
//        if (response != null) {}

        response.disconnect();
    }

    private boolean checkRateLimit() {
        long diff = ChronoUnit.MINUTES.between(lastUpdated, LocalDateTime.now());
        // 1분 미만이면,
        if (diff < 1) {
            counter.incrementAndGet();
            // RATE_LIMIT 초과하면?
            if (counter.intValue() > RATE_LIMIT) {
                logger.debug("CoinGecko API call Rate limit has been reached");
                return false;
            }
            logger.debug("CoinGecko API call Rate limit count : {}", counter);
            return true;
        } else {
            counter.set(0);
            logger.debug("CoinGecko API call Rate limit reset");
            return true;
        }
    }
}
