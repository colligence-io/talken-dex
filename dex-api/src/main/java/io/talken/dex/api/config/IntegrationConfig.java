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

/**
 * The type Integration config.
 */
@Configuration
public class IntegrationConfig {
	@Autowired
	private ApiSettings apiSettings;

    /**
     * Admin alarm service admin alarm service.
     *
     * @return the admin alarm service
     */
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

    /**
     * Talken wallet service talken wallet service.
     *
     * @return the talken wallet service
     */
    @Bean
	public TalkenWalletService talkenWalletService() {
		return new TalkenWalletService(apiSettings.getIntegration().getWallet().getApiUrl());
	}

    /**
     * Anchor server service anchor server service.
     *
     * @return the anchor server service
     */
    @Bean
	public AnchorServerService anchorServerService() {
		return new AnchorServerService(apiSettings.getIntegration().getAnchor().getApiUrl());
	}

    /**
     * Sign server service sign server service.
     *
     * @return the sign server service
     */
    @Bean
	public SignServerService signServerService() {
		final DexSettings._Integration._SignServer ssConfig = apiSettings.getIntegration().getSignServer();
		return new SignServerService(ssConfig.getAddr(), ssConfig.getAppName(), ssConfig.getAppKey());
	}
}
