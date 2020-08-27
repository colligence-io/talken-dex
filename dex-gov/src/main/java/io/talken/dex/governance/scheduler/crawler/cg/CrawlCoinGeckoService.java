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
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Iterator;
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
    @Scheduled(fixedDelay = 1000 * 60 * 60 * 12, initialDelay = 1000)
    private void crawl() {
        if(DexGovStatus.isStopped) return;
        logger.debug("CoinGecko CrawlerService started at : {}", UTCUtil.getNow());
        counter.incrementAndGet();
        try {
            if (checkRateLimit()) {
                getMarketCapData("global");
                if(counter.get() % 24 == 0) {
                    getMarketCapData("global");
                } else {
                    logger.debug("Skip CMC crawler task for saving credit.");
                }
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

//        logger.debug("Response CoinGecko marketCap data as ParseStream {}", cgmcr);

        try {
            if (mongoTemplate.getCollection(COLLECTION_NAME).countDocuments() == 0) {
                LinkedHashMap currentDoc = (LinkedHashMap) cgmcr.getData();

                // init
                LinkedHashMap totalMarketCap = (LinkedHashMap) currentDoc.get("total_market_cap");
                LinkedHashMap tmcMap = new LinkedHashMap<>();
                Iterator tmcIter = totalMarketCap.keySet().iterator();
                while(tmcIter.hasNext()) {
                    Object key = tmcIter.next();
                    Object value = totalMarketCap.get(key);
                    logger.debug("totalMarketCap {} / {}", key, value);
                    tmcMap.put(key, 0);
                }

                LinkedHashMap totalVolume = (LinkedHashMap) currentDoc.get("total_volume");
                LinkedHashMap tvMap = new LinkedHashMap<>();
                Iterator tvIter = totalVolume.keySet().iterator();
                while(tvIter.hasNext()) {
                    Object key = tvIter.next();
                    Object value = totalVolume.get(key);
                    logger.debug("totalVolume {} / {}", key, value);
                    tvMap.put(key, 0);
                }

                long currentUpdatedAt = Long.valueOf(currentDoc.getOrDefault("updated_at", 0).toString());

                currentDoc.put("total_market_cap_per", tmcMap);
                currentDoc.put("total_volume_per", tvMap);
                currentDoc.put("update_count", 0);
                currentDoc.put("last_update", LocalDate.from(UTCUtil.ts2ldt(currentUpdatedAt)));

                mongoTemplate.save(currentDoc, COLLECTION_NAME);
            } else {
                LinkedHashMap currentDoc = (LinkedHashMap) cgmcr.getData();
                MongoCollection<Document> collection = mongoTemplate.getCollection(COLLECTION_NAME);
                Document lastDoc = collection.find().sort(new BasicDBObject("updated_at", -1)).first();

                long currentUpdatedAt = Long.valueOf(currentDoc.getOrDefault("updated_at", 0).toString());
                long lastUpdatedAt = Long.valueOf(lastDoc.getOrDefault("updated_at", 0).toString());

                if (lastUpdatedAt != currentUpdatedAt) {
                    LocalDateTime curr = UTCUtil.ts2ldt(currentUpdatedAt);
                    LocalDateTime last = UTCUtil.ts2ldt(lastUpdatedAt);

                    LocalDate currld = LocalDate.from(curr);
                    LocalDate lastld = LocalDate.from(last);

                    // TODO: document 어제, 오늘 두개 만들어서 24h per 계산.
                    if (currld.isEqual(lastld)) {
                        int last_update_count = (int) lastDoc.getOrDefault("update_count", 0);
                        last_update_count++;
                        currentDoc.put("total_market_cap_per", lastDoc.get("total_market_cap_per"));
                        currentDoc.put("total_volume_per", lastDoc.get("total_volume_per"));
                        currentDoc.put("update_count", last_update_count);
                        currentDoc.put("last_update", lastld);

                        Query query = new Query(Criteria.where("_id").is(lastDoc.get("_id")));
                        Document doc = copyDocument(currentDoc);
                        Update update = Update.fromDocument(doc);

                        mongoTemplate.upsert(query, update, COLLECTION_NAME);
                    } else {
                        // TODO : insert
                        ObjectMapper objectMapper = new ObjectMapper();

                        LinkedHashMap totalMarketCap = (LinkedHashMap) currentDoc.get("total_market_cap");
                        LinkedHashMap lastTotalMarketCap = objectMapper.convertValue(lastDoc.get("total_market_cap"), LinkedHashMap.class);

                        LinkedHashMap tmcMap = new LinkedHashMap<>();
                        Iterator tmcIter = totalMarketCap.keySet().iterator();
                        while(tmcIter.hasNext()) {
                            Object key = tmcIter.next();
                            Object currValue = totalMarketCap.get(key);
                            Object lastValue = lastTotalMarketCap.getOrDefault(key, null);
                            if (lastValue != null) {
                                String v = getPer(lastValue.toString(), currValue.toString());
                                tmcMap.put(key, v);
                            } else {
                                tmcMap.put(key, 0);
                            }
//                            logger.debug("totalMarketCap {} : {} = {}", key, currValue, lastValue);
                        }

                        LinkedHashMap totalVolume = (LinkedHashMap) currentDoc.get("total_volume");
                        LinkedHashMap lastTotalVolume = objectMapper.convertValue(lastDoc.get("total_volume"), LinkedHashMap.class);
                        LinkedHashMap tvMap = new LinkedHashMap<>();
                        Iterator tvIter = totalVolume.keySet().iterator();
                        while(tvIter.hasNext()) {
                            Object key = tvIter.next();
                            Object currValue = totalVolume.get(key);
                            Object lastValue = lastTotalVolume.getOrDefault(key, null);
                            if (lastValue != null) {
                                String v = getPer(lastValue.toString(), currValue.toString());
                                tvMap.put(key, v);
                            } else {
                                tvMap.put(key, 0);
                            }
//                            logger.debug("totalVolume {} : {} = {}", key, currValue, lastValue);
                        }

                        currentDoc.put("total_market_cap_per", tmcMap);
                        currentDoc.put("total_volume_per", tvMap);
                        currentDoc.put("update_count", 0);
                        currentDoc.put("last_update", currld);

                        mongoTemplate.save(currentDoc, COLLECTION_NAME);
                    }
                }
            }
        } catch(Exception ex) {
            logger.exception(ex);
        }

//        if (response != null) {
            response.disconnect();
//        }
    }

    private String getPer(String l, String r) {
        BigDecimal lv = new BigDecimal(l);
        BigDecimal rv = new BigDecimal(r);
        return lv.subtract(rv)
                .divide(rv, MathContext.DECIMAL32)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, BigDecimal.ROUND_UP)
                .toPlainString();
    }

    private Document copyDocument(Map map) {
        BasicDBObject bObject = new BasicDBObject();
        for (Object key : map.keySet()) {
            Object value = map.get(key);
            bObject.append((String) key, value);
        }
        return new Document(bObject);
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
