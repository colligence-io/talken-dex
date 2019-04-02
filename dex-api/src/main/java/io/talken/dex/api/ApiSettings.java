package io.talken.dex.api;

import io.talken.common.persistence.vault.VaultSecretReader;
import io.talken.common.persistence.vault.data.VaultSecretDataDexSettings;
import io.talken.common.persistence.vault.data.VaultSecretDataWebJwt;
import io.talken.dex.shared.DexTaskId;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@ConfigurationProperties("talken.dex-api")
@Getter
@Setter
public class ApiSettings {
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

	private _Server server;

	@Getter
	@Setter
	public static class _Server {
		private String rlyAddress;
		private String wltAddress;
		private String ancAddress;
		private String txtAddress;
		private String txtServerId;
	}

	private _Fee fee;

	@Getter
	@Setter
	public static class _Fee {
		private double offerFeeRate;
		private double offerFeeRateCtxFactor;

		private String deanchorFeePivotAsset;
		private double deanchorFeeAmount;
		private double deanchorFeeRateCtxFactor;

		private int refundRetryInterval;
		private int refundMaxRetry;
	}
}
