package io.talken.dex.api.config;

import io.talken.common.persistence.vault.TalkenVaultConfiguration;
import io.talken.common.persistence.vault.VaultSetting;
import io.talken.common.util.PrefixedLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
public class VaultConfig extends TalkenVaultConfiguration {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(VaultConfig.class);
	@Value("${vault.uri}")
	private URI vaultUri;

	@Value("${vault.app-role}")
	private String appRole;

	@Value("${vault.hostname:}")
	private String hostname;

	@Value("${vault.secret-key}")
	private String password;

	@Override
	protected URI getVaultUri() {
		return vaultUri;
	}

	@Override
	protected VaultSetting getSetting() {
		return new VaultSetting(appRole, hostname, password);
	}
}
