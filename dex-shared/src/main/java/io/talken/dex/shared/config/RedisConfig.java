package io.talken.dex.shared.config;

import io.talken.common.persistence.vault.VaultSecretReader;
import io.talken.common.persistence.vault.data.VaultSecretDataRedis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * The type Redis config.
 */
@Configuration
public class RedisConfig {
	@Autowired
	private VaultSecretReader secretReader;

    /**
     * Redis connection factory lettuce connection factory.
     *
     * @return the lettuce connection factory
     */
    @Bean
	public LettuceConnectionFactory redisConnectionFactory() {
		VaultSecretDataRedis secret = secretReader.readSecret("redis", VaultSecretDataRedis.class);

		RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
		redisConfig.setHostName(secret.getHost());
		redisConfig.setPort(secret.getPort());
		if(secret.getPassword() != null) redisConfig.setPassword(secret.getPassword());

		return new LettuceConnectionFactory(redisConfig);
	}

    /**
     * Redis template redis template.
     *
     * @return the redis template
     */
    @Bean
	public RedisTemplate<String, Object> redisTemplate() {
		RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
		redisTemplate.setConnectionFactory(redisConnectionFactory());
		redisTemplate.setKeySerializer(new StringRedisSerializer());
		redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
		return redisTemplate;
	}

    /**
     * Redis message listener container redis message listener container.
     *
     * @return the redis message listener container
     */
    @Bean
    @Primary
	public RedisMessageListenerContainer redisMessageListenerContainer() {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(redisConnectionFactory());
		return container;
	}
}
