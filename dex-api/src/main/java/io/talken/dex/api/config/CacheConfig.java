package io.talken.dex.api.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {
	@Autowired
	private LettuceConnectionFactory lettuceConnectionFactory;

	@Bean
	public RedisCacheConfiguration defaultRedisCacheConfiguration() {
		return buildCacheConfiguration(Duration.ofSeconds(60));
	}

	@Bean
	public Map<String, RedisCacheConfiguration> redisCacheConfigurationMap() {
		Map<String, RedisCacheConfiguration> cfgMap = new HashMap<>();

		cfgMap.put("swapPredictSet", buildCacheConfiguration(Duration.ofSeconds(3)));

		return cfgMap;
	}

	private RedisCacheConfiguration buildCacheConfiguration(Duration expires) {
		return RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(expires)
				.serializeKeysWith(
						RedisSerializationContext.SerializationPair.fromSerializer(
								new StringRedisSerializer()
						))
				.serializeValuesWith(
						RedisSerializationContext.SerializationPair.fromSerializer(
								new GenericJackson2JsonRedisSerializer()
						)
				)
				.disableCachingNullValues();
	}

	@Bean
	public RedisCacheManager cacheManager() {
		RedisCacheManager rcm = RedisCacheManager.builder(lettuceConnectionFactory)
				.transactionAware()
				.cacheDefaults(defaultRedisCacheConfiguration())
				.withInitialCacheConfigurations(redisCacheConfigurationMap())
				.build();
		return rcm;
	}
}
