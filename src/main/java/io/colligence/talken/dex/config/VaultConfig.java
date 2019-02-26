package io.colligence.talken.dex.config;

import io.colligence.talken.common.persistence.vault.TalkenVaultConfiguration;
import io.colligence.talken.common.persistence.vault.VaultSetting;
import io.colligence.talken.common.util.PrefixedLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.client.VaultEndpoint;

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
	public VaultEndpoint vaultEndpoint() {
		return VaultEndpoint.from(vaultUri);
	}

	@Override
	protected VaultSetting getSetting() {
		return new VaultSetting(appRole, hostname, password);
	}
}
