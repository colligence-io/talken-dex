package io.talken.dex.api.config;

import io.talken.dex.api.ApiSettings;
import io.talken.dex.shared.service.integration.anchor.AnchorServerService;
import io.talken.dex.shared.service.integration.wallet.TalkenWalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IntegrationConfig {
	@Autowired
	private ApiSettings apiSettings;

	@Bean
	public TalkenWalletService talkenWalletService() {
		return new TalkenWalletService(apiSettings.getIntegration().getWallet().getApiUrl());
	}

	@Bean
	public AnchorServerService anchorServerService() {
		return new AnchorServerService(apiSettings.getIntegration().getAnchor().getApiUrl());
	}
}
