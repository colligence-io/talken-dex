package io.talken.dex.config;

import io.talken.dex.DexSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class ScheduleConfig {
	@Autowired
	private DexSettings dexSettings;

	@Bean(name = "taskScheduler")
	public ThreadPoolTaskScheduler taskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(dexSettings.getScheduler().getPoolSize());
		scheduler.setThreadNamePrefix("SCHEDULER");
		return scheduler;
	}
}
