package io.talken.dex.api.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.dto.TotalMarketCapResult;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.TokenMetaTableService;
import io.talken.dex.shared.exception.TokenMetaLoadException;
import org.bson.Document;
import org.bson.json.Converter;
import org.bson.json.JsonWriterSettings;
import org.bson.json.StrictJsonWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;

@Service
@Scope("singleton")
public class TokenMetaApiService extends TokenMetaTableService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TokenMetaApiService.class);

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
					Optional.ofNullable(redisTemplate.opsForValue().get(TokenMetaTable.REDIS_UPDATED_KEY))
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

    public TotalMarketCapResult getTotalMarketCapInfo() {
        MongoCollection<Document> collection = mongoTemplate.getCollection(COLLECTION_NAME);
        Document lastDoc = collection.find().sort(new BasicDBObject("updated_at", -1)).first();

        if (lastDoc != null) {
            Gson gson = new Gson();
            JsonWriterSettings settings = JsonWriterSettings.builder()
                    .objectIdConverter((value, writer) -> writer.writeString(value.toHexString()))
                    .dateTimeConverter(new JsonDateTimeConverter())
                    .int64Converter((value, writer) -> writer.writeNumber(value.toString()))
                    .build();

            String json = lastDoc.toJson(settings);
            TotalMarketCapResult totalMarketCapResult = gson.fromJson(json, TotalMarketCapResult.class);
            return totalMarketCapResult;
        } else {
            return null;
        }
    }

    public static class JsonDateTimeConverter implements Converter<Long> {
        static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT
                .withZone(ZoneId.of("UTC"));

        @Override
        public void convert(Long value, StrictJsonWriter writer) {
            try {
                Instant instant = new Date(value).toInstant();
                String s = DATE_TIME_FORMATTER.format(instant);
                writer.writeString(s);
            } catch (Exception e) {
                logger.error(String.format("Fail to convert offset %d to JSON date", value), e);
            }
        }
    }
}
