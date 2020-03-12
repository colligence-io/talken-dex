package io.talken.dex.api.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.dto.TotalMarketCapResult;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.TokenMetaTableService;
import io.talken.dex.shared.exception.TokenMetaLoadException;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Optional;

@Service
@Scope("singleton")
public class TokenMetaService extends TokenMetaTableService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TokenMetaService.class);

    private static final String COLLECTION_NAME = "coin_gecko";

    @Autowired
    private MongoTemplate mongoTemplate;

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	private static Long loadTimestamp;

	@PostConstruct
	private void init() throws TokenMetaLoadException {
		checkAndReload();
	}

	@Scheduled(fixedDelay = 1000, initialDelay = 5000)
	private void checkSchedule() throws TokenMetaLoadException {
		checkAndReload();
	}

	/**
	 * check and reload meta if updated
	 *
	 * @throws TokenMetaLoadException
	 */
	protected void checkAndReload() throws TokenMetaLoadException {
		try {
			Long redisTmUpdated =
					Optional.ofNullable(redisTemplate.opsForValue().get(TokenMetaTable.REDIS_UDPATED_KEY))
							.map((o) -> Long.valueOf(o.toString()))
							.orElseThrow(() -> new TokenMetaLoadException("cannot find cached meta"));

			if(!redisTmUpdated.equals(loadTimestamp)) {
				ObjectMapper mapper = new ObjectMapper();
				mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

				updateStorage((TokenMetaTable) redisTemplate.opsForValue().get(TokenMetaTable.REDIS_KEY));
				loadTimestamp = redisTmUpdated;
			}
		} catch(TokenMetaLoadException ex) {
			throw ex;
		} catch(Exception ex) {
			throw new TokenMetaLoadException(ex);
		}
	}

    public TotalMarketCapResult getTotalMarketCapInfo(String marketCapSymbol, String marketVolSymbol, String marketPerSymbol) {
        TotalMarketCapResult totalMarketCapResult = new TotalMarketCapResult();
        MongoCollection<Document> collection = mongoTemplate.getCollection(COLLECTION_NAME);
        Document lastDoc = collection.find().sort(new BasicDBObject("_id", -1)).first();

        Double tmc = lastDoc.get("total_market_cap", Document.class).get(marketCapSymbol, Double.class);
        Double tv = lastDoc.get("total_volume", Document.class).get(marketVolSymbol, Double.class);
        Double mcp = lastDoc.get("market_cap_percentage", Document.class).get(marketPerSymbol, Double.class);
        Double mccPer24 = lastDoc.get("market_cap_change_percentage_24h_usd", Double.class);

        totalMarketCapResult.setTotalMarketCap(String.format("%1$,.6f", tmc));
        totalMarketCapResult.setTotalVolume(String.format("%1$,.6f", tv));
        totalMarketCapResult.setMarketCapPer(String.format("%1$,.6f", mcp));
        totalMarketCapResult.setMarketCapChangePercentage24hUsd(String.format("%1$,.6f", mccPer24));

        return totalMarketCapResult;
    }
}
