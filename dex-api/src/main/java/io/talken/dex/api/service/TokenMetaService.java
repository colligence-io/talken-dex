package io.talken.dex.api.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.TokenMetaTableService;
import io.talken.dex.shared.exception.TokenMetaLoadException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Optional;

@Service
@Scope("singleton")
public class TokenMetaService extends TokenMetaTableService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TokenMetaService.class);

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
}
