package io.talken.dex.governance.config;

import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.GovSettings;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.service.integration.anchor.AnchorServerService;
import io.talken.dex.shared.service.integration.signer.SignServerService;
import io.talken.dex.shared.service.integration.wallet.TalkenWalletService;
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

	@Bean
	public TalkenWalletService talkenWalletService() {
		return new TalkenWalletService(govSettings.getIntegration().getWallet().getApiUrl());
	}

	@Bean
	public AnchorServerService anchorServerService() {
		return new AnchorServerService(govSettings.getIntegration().getAnchor().getApiUrl());
	}

	@Bean
	public SignServerService signServerService() {
		final DexSettings._Integration._SignServer ssConfig = govSettings.getIntegration().getSignServer();
		return new SignServerService(ssConfig.getAddr(), ssConfig.getAppName(), ssConfig.getAppKey());
	}
}
