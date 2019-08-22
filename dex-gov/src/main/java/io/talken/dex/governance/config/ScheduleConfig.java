package io.talken.dex.governance.config;

import io.talken.dex.governance.GovSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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
		scheduler.setThreadNamePrefix("DGOV-SCDLR-");
		return scheduler;
	}

	@Bean(name = "taskExecutor")
	public TaskExecutor taskExecutor() {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setCorePoolSize(govSettings.getScheduler().getPoolSize());
		taskExecutor.setMaxPoolSize(govSettings.getScheduler().getMaxPoolSize());
		taskExecutor.setQueueCapacity(govSettings.getScheduler().getQueueCapacity());
		taskExecutor.setThreadNamePrefix("GOV-EXCTR-");
		taskExecutor.initialize();
		return taskExecutor;
	}
}
