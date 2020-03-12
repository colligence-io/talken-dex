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
import com.mongodb.client.result.DeleteResult;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.DexGovStatus;
import org.bson.Document;
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

    // 60 min = 1000 * 60 * 60
    @Scheduled(fixedDelay = 1000 * 60 * 60, initialDelay = 1000)
    private void crawl() {
        if(DexGovStatus.isStopped) return;
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

//        logger.debug("Request CoinGecko marketCap data API.");
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
        CoinGeckoMarketCapResult cgmcr = mapper.readValue(response.parseAsString(), CoinGeckoMarketCapResult.class);
//        logger.debug("Response CoinGecko marketCap data as ParseStream {}", cgmcr);

        try {
            if (mongoTemplate.getCollection(COLLECTION_NAME).countDocuments() == 0) {
                mongoTemplate.save(cgmcr.getData(), COLLECTION_NAME);
            } else {
                LinkedHashMap currentDoc = (LinkedHashMap) cgmcr.getData();
                MongoCollection<Document> collection = mongoTemplate.getCollection(COLLECTION_NAME);
                Document lastDoc = collection.find().sort(new BasicDBObject("_id", -1)).first();

                long currentUpdatedAt = (int) currentDoc.get("updated_at");
                long lastUpdatedAt = (int) lastDoc.get("updated_at");

                if (lastUpdatedAt < currentUpdatedAt) {
                    LocalDateTime curr = UTCUtil.ts2ldt(currentUpdatedAt);
                    LocalDateTime last = UTCUtil.ts2ldt(lastUpdatedAt);
                    // TODO: document 어제, 오늘 두개 만들어서 24h per 계산.
//                    if (ChronoUnit.DAYS.between(curr, last) == 0) {
//                        BasicDBObject bObject = new BasicDBObject();
//                        bObject.put("updated_at", new BasicDBObject("$lt", currentUpdatedAt));
//                        DeleteResult dResult = collection.deleteMany(bObject);
//                        logger.debug("Cleanup Documents Every 24h {}", dResult);
//                    }
                    BasicDBObject bObject = new BasicDBObject();
                    bObject.put("updated_at", new BasicDBObject("$lt", currentUpdatedAt));
                    DeleteResult dResult = collection.deleteMany(bObject);
                    logger.debug("Cleanup Documents {}", dResult);

                    mongoTemplate.save(cgmcr.getData(), COLLECTION_NAME);
                }
            }
        } catch(Exception ex) {
            logger.exception(ex);
        }

        if (response != null) {
            response.disconnect();
        }
    }

    private boolean checkRateLimit() {
        long minutes = ChronoUnit.MINUTES.between(lastUpdated, LocalDateTime.now());
        if (minutes < 10) {
            if (counter.intValue() < RATE_LIMIT) {
                counter.addAndGet(1);
                return true;
            } else {
                counter.set(0);
                logger.debug("CoinGecko API call Rate limit has been reached");
                return false;
            }
        } else {
            counter.set(0);
            logger.debug("CoinGecko API call Rate limit reset");
            return true;
        }
    }
}
