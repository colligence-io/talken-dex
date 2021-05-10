package io.talken.dex.api.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.jooq.tables.pojos.UserContract;
import io.talken.common.persistence.jooq.tables.records.UserContractRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.controller.dto.ContractListResult;
import io.talken.dex.api.controller.dto.ContractRequest;
import io.talken.dex.api.controller.dto.TotalMarketCapResult;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.TokenMetaTableService;
import io.talken.dex.shared.exception.TokenMetaLoadException;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.json.Converter;
import org.bson.json.JsonWriterSettings;
import org.bson.json.StrictJsonWriter;
import org.jooq.DSLContext;
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
import java.util.stream.Collectors;

import static io.talken.common.persistence.jooq.Tables.USER_CONTRACT;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class TokenMetaApiService extends TokenMetaTableService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TokenMetaApiService.class);

    private static final String COLLECTION_NAME = "coin_gecko";

    private final MongoTemplate mongoTemplate;

	private final RedisTemplate<String, Object> redisTemplate;

	private static Long loadTimestamp;

    private final DSLContext dslContext;

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

    public Boolean addContract(User user, ContractRequest postBody) {
        UserContractRecord ucRecord = new UserContractRecord();

        ucRecord.setUserId(user.getId());
        ucRecord.setBctxType(postBody.getBctxType());
        ucRecord.setContractType(postBody.getContractType());
        ucRecord.setContractAddress(postBody.getContractAddress());
        ucRecord.setName(postBody.getName());
        ucRecord.setSymbol(postBody.getSymbol());
        ucRecord.setDecimals(postBody.getDecimals());
        dslContext.attach(ucRecord);
        ucRecord.store();

	    return true;
    }

    public Boolean removeContract(User user, String contract) {

        UserContractRecord ucRecord = dslContext.selectFrom(USER_CONTRACT)
                .where(USER_CONTRACT.USER_ID.eq(user.getId())
                        .and(USER_CONTRACT.CONTRACT_ADDRESS.eq(contract)))
                .fetchAny();

        dslContext.attach(ucRecord);
        ucRecord.delete();

        return true;
    }

    public ContractListResult listContract(User user) {
        ContractListResult result = new ContractListResult();
        result.setRows(dslContext.selectFrom(USER_CONTRACT)
                .where(USER_CONTRACT.USER_ID.eq(user.getId()))
                .fetch()
                .stream()
                .map(r -> new ContractRequest(r.into(USER_CONTRACT).into(UserContract.class)))
                .collect(Collectors.toList()));

        result.setTotal(result.getRows().size());
        result.setPageLimit(-1);
        result.setTotalPage(-1);
        return result;
    }
}
