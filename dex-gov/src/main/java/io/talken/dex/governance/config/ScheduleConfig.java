package io.talken.dex.governance.config;

import io.talken.dex.governance.GovSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class ScheduleConfig {
	@Autowired
	private GovSettings govSettings;

	@Bean(name = "taskScheduler")
	public ThreadPoolTaskScheduler taskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(govSettings.getScheduler().getPoolSize());
		scheduler.setThreadNamePrefix("SCHEDULER");
		return scheduler;
	}
}
