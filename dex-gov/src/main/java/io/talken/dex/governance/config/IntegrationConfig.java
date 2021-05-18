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

/**
 * The type Integration config.
 */
@Configuration
public class IntegrationConfig {
	@Autowired
	private GovSettings govSettings;

    /**
     * Admin alarm service admin alarm service.
     *
     * @return the admin alarm service
     */
    @Bean
	public AdminAlarmService adminAlarmService() {
		return new AdminAlarmService("Talken DEX Governance", govSettings.getIntegration().getSlack().getAlarmWebHook());
	}

    /**
     * Talken wallet service talken wallet service.
     *
     * @return the talken wallet service
     */
    @Bean
	public TalkenWalletService talkenWalletService() {
		return new TalkenWalletService(govSettings.getIntegration().getWallet().getApiUrl());
	}

    /**
     * Anchor server service anchor server service.
     *
     * @return the anchor server service
     */
    @Bean
	public AnchorServerService anchorServerService() {
		return new AnchorServerService(govSettings.getIntegration().getAnchor().getApiUrl());
	}

    /**
     * Sign server service sign server service.
     *
     * @return the sign server service
     */
    @Bean
	public SignServerService signServerService() {
		final DexSettings._Integration._SignServer ssConfig = govSettings.getIntegration().getSignServer();
		return new SignServerService(ssConfig.getAddr(), ssConfig.getAppName(), ssConfig.getAppKey());
	}
}
