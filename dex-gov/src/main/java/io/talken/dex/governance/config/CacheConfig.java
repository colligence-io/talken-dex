package io.talken.dex.governance.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching//Using redis, settings on application.yml
public class CacheConfig {

}
