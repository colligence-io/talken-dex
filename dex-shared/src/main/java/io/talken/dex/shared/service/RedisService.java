package io.talken.dex.shared.service;

import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.collection.ObjectPair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Scope("singleton")
public class RedisService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(RedisService.class);

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	public <T> ObjectPair<Long, T> getData(String dataKey, Class<T> dataClass, String timestampKey) {
		return getData(dataKey, dataClass, timestampKey, null);
	}

	public <T> ObjectPair<Long, T> getData(String dataKey, Class<T> dataClass, String timestampKey, Long timestamp) {
		Long dataUpdated = null;
		Object updatedVal = redisTemplate.opsForValue().get(timestampKey);
		dataUpdated = Long.valueOf(updatedVal.toString());

		Object data = redisTemplate.opsForValue().get(dataKey);
		return new ObjectPair<>(dataUpdated, dataClass.cast(data));
	}

	public void updateData(String dataKey, Object data, String timestampKey, Long timestamp) {
		redisTemplate.opsForValue().set(timestampKey, timestamp);
		redisTemplate.opsForValue().set(dataKey, data);
	}
}
