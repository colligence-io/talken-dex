package io.talken.dex.governance.config;

import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.GovSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IntegrationConfig {
	@Autowired
	private GovSettings govSettings;

	@Bean
	public AdminAlarmService adminAlarmService() {
		return new AdminAlarmService("Talken DEX Governance", govSettings.getIntegration().getSlack().getAlarmWebHook());
	}
}
