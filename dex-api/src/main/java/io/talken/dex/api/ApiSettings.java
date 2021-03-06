package io.talken.dex.api;

import io.talken.common.persistence.vault.VaultSecretReader;
import io.talken.common.persistence.vault.data.VaultSecretDataWebJwt;
import io.talken.dex.shared.DexSettings;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * The type Api settings.
 */
@Component
@ConfigurationProperties("talken.dex")
@Getter
@Setter
public class ApiSettings extends DexSettings {
	@Autowired
	private VaultSecretReader secretReader;

	@PostConstruct
	private void readVaultSecret() {
		VaultSecretDataWebJwt secret2 = secretReader.readSecret("web-jwt", VaultSecretDataWebJwt.class);
		accessToken.jwtSecret = secret2.getSecret();
		accessToken.jwtExpiration = secret2.getExpiration();
	}

	private _AccessToken accessToken;

    /**
     * The type Access token.
     */
    @Getter
	@Setter
	public static class _AccessToken {
		private String tokenHeader;
		private String jwtSecret;
		private int jwtExpiration;
	}
}
