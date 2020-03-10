package io.talken.dex.governance.scheduler.crawler.cg;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.dex.governance.DexGovStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static io.talken.dex.governance.scheduler.crawler.cg.CoinGeckoMarketCapResult.dataListBuilder;

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

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final String apiUrl = "https://api.coingecko.com/api/v3";
    private Map<String, String> apis = new HashMap<>();

    private static AtomicLong counter = new AtomicLong(0);
    private LocalDateTime lastUpdated = LocalDateTime.now();

    public CrawlCoinGeckoService() {
        this.apis.put("global", "/global");
    }

    // 10 min = 1000 * 60 * 10
    @Scheduled(fixedDelay = 1000 * 10, initialDelay = 1000)
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
        CoinGeckoMarketCapResult result = new CoinGeckoMarketCapResult();

        logger.debug("Request CoinGecko marketCap data API.");
        HttpResponse response = requestFactory
                .buildGetRequest(new GenericUrl(apiUrl+api))
                .execute();

        JsonObject data = JsonParser.parseReader(new InputStreamReader(response.getContent())).getAsJsonObject().getAsJsonObject("data");
        logger.debug("Response CoinGecko marketCap data as JsonParseStream {}", data);

        JsonObject totalMarketCaps = data.getAsJsonObject("total_market_cap");
        JsonObject totalVolumes = data.getAsJsonObject("total_volume");
        JsonObject marketCapPercentage = data.getAsJsonObject("market_cap_percentage");
        BigDecimal marketCapChangePercentage24hUsd = data.get("market_cap_change_percentage_24h_usd").getAsBigDecimal();
        long updatedAt = data.get("updated_at").getAsLong();

        result.setTotalMarketCap(dataListBuilder(totalMarketCaps));
        result.setTotalVolume(dataListBuilder(totalVolumes));
        result.setMarketCapPercentage(dataListBuilder(marketCapPercentage));
        result.setMarketCapChangePer24hUSD(marketCapChangePercentage24hUsd);
        result.setUpdatedAt(UTCUtil.ts2ldt(updatedAt));

        logger.debug("Response CoinGecko marketCap data parse result {}", result);

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
