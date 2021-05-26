package io.talken.dex.shared.config;

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

/**
 * The type Cache config.
 */
@Configuration
@EnableCaching//Using redis, settings on application.yml
public class CacheConfig {
    /**
     * The type Cache names.
     */
    public static class CacheNames {
        /**
         * The constant SWAP_PREDICT_SET.
         */
        public static final String SWAP_PREDICT_SET = "swapPredictSet";
        /**
         * The constant ETH_ERC20_CONTRACT_INFO.
         */
        public static final String ETH_ERC20_CONTRACT_INFO = "eth_erc20ContractInfo";
        /**
         * The constant LUK_ERC20_CONTRACT_INFO.
         */
        public static final String LUK_ERC20_CONTRACT_INFO = "luk_erc20ContractInfo";
        /**
         * The constant BSC_BEP20_CONTRACT_INFO.
         */
        public static final String BSC_BEP20_CONTRACT_INFO = "bsc_bep20ContractInfo";
        /**
         * The constant HECO_HRC20_CONTRACT_INFO.
         */
        public static final String HECO_HRC20_CONTRACT_INFO = "heco_hrc20ContractInfo";
        /**
         * The constant KLAY_KIP7_CONTRACT_INFO.
         */
        public static final String KLAY_KIP7_CONTRACT_INFO = "klay_kip7ContractInfo";
	}

	@Autowired
	private LettuceConnectionFactory lettuceConnectionFactory;

    /**
     * Default redis cache configuration redis cache configuration.
     *
     * @return the redis cache configuration
     */
    @Bean
	public RedisCacheConfiguration defaultRedisCacheConfiguration() {
		return buildCacheConfiguration(Duration.ofSeconds(60));
	}

    /**
     * Redis cache configuration map map.
     *
     * @return the map
     */
    @Bean
	public Map<String, RedisCacheConfiguration> redisCacheConfigurationMap() {
		Map<String, RedisCacheConfiguration> cfgMap = new HashMap<>();

		cfgMap.put(CacheNames.SWAP_PREDICT_SET, buildCacheConfiguration(Duration.ofSeconds(3))); // 3 seconds
		cfgMap.put(CacheNames.ETH_ERC20_CONTRACT_INFO, buildCacheConfiguration(Duration.ZERO)); // eternal cache
		cfgMap.put(CacheNames.LUK_ERC20_CONTRACT_INFO, buildCacheConfiguration(Duration.ZERO)); // eternal cache
		cfgMap.put(CacheNames.BSC_BEP20_CONTRACT_INFO, buildCacheConfiguration(Duration.ZERO)); // eternal cache
		cfgMap.put(CacheNames.HECO_HRC20_CONTRACT_INFO, buildCacheConfiguration(Duration.ZERO)); // eternal cache
        cfgMap.put(CacheNames.KLAY_KIP7_CONTRACT_INFO, buildCacheConfiguration(Duration.ZERO)); // eternal cache

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

    /**
     * Cache manager redis cache manager.
     *
     * @return the redis cache manager
     */
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
