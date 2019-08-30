package io.talken.dex.api;

import io.talken.common.persistence.vault.VaultSecretReader;
import io.talken.common.persistence.vault.data.VaultSecretDataDexSettings;
import io.talken.common.persistence.vault.data.VaultSecretDataWebJwt;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.DexTaskId;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@ConfigurationProperties("talken.dex")
@Getter
@Setter
public class ApiSettings extends DexSettings {
	@Autowired
	private VaultSecretReader secretReader;

	@PostConstruct
	private void readVaultSecret() {
		VaultSecretDataDexSettings secret = secretReader.readSecret("dexSettings", VaultSecretDataDexSettings.class);
		randomStringTable = secret.getTaskIdSeed();
		DexTaskId.init(randomStringTable);

		VaultSecretDataWebJwt secret2 = secretReader.readSecret("web-jwt", VaultSecretDataWebJwt.class);
		accessToken.jwtSecret = secret2.getSecret();
		accessToken.jwtExpiration = secret2.getExpiration();
	}

	private String randomStringTable;

	private _AccessToken accessToken;

	@Getter
	@Setter
	public static class _AccessToken {
		private String tokenHeader;
		private String jwtSecret;
		private int jwtExpiration;
	}
}
