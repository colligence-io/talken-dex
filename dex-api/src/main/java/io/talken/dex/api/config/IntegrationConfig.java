package io.talken.dex.api.config;

import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.api.ApiSettings;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.service.integration.anchor.AnchorServerService;
import io.talken.dex.shared.service.integration.signer.SignServerService;
import io.talken.dex.shared.service.integration.wallet.TalkenWalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;

@Configuration
public class IntegrationConfig {
	@Autowired
	private ApiSettings apiSettings;

	@Bean
	public AdminAlarmService adminAlarmService() {
		String hostname;
		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch(Exception ex) {
			hostname = "N/A";
		}

		return new AdminAlarmService("Talken DEX API [" + hostname + "]", apiSettings.getIntegration().getSlack().getAlarmWebHook());
	}

	@Bean
	public TalkenWalletService talkenWalletService() {
		return new TalkenWalletService(apiSettings.getIntegration().getWallet().getApiUrl());
	}

	@Bean
	public AnchorServerService anchorServerService() {
		return new AnchorServerService(apiSettings.getIntegration().getAnchor().getApiUrl());
	}

	@Bean
	public SignServerService signServerService() {
		final DexSettings._Integration._SignServer ssConfig = apiSettings.getIntegration().getSignServer();
		return new SignServerService(ssConfig.getAddr(), ssConfig.getAppName(), ssConfig.getAppKey());
	}
}
