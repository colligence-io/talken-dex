package io.colligence.talken.dex;

import io.colligence.talken.common.persistence.vault.VaultSecretDataDexKey;
import io.colligence.talken.common.persistence.vault.VaultSecretDataWebJwt;
import io.colligence.talken.dex.config.VaultConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties("talken.dex")
@Getter
@Setter
public class DexSettings {
	@Autowired
	private VaultConfig.VaultSecretReader secretReader;

	@PostConstruct
	private void readVaultSecret() {
		VaultSecretDataDexKey secret = secretReader.readSecret("dexKey", VaultSecretDataDexKey.class);
		randomStringTable = secret.getSeed();

		VaultSecretDataWebJwt secret2 = secretReader.readSecret("web-jwt", VaultSecretDataWebJwt.class);
		accessToken.jwtSecret = secret2.getSecret();
		accessToken.jwtExpiration = secret2.getExpiration();
	}

	private String randomStringTable;

	@Getter
	@Setter
	public static class _AccessToken {
		private String tokenHeader;
		private String jwtSecret;
		private int jwtExpiration;
	}

	private _Scheduler scheduler;

	@Getter
	@Setter
	public static class _Scheduler {
		private int poolSize;
	}

	private _AccessToken accessToken;

	private _Stellar stellar;

	@Getter
	@Setter
	public static class _Stellar {
		private String network;
		private List<String> serverList;
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
	}

	private Map<String, String> signerMock;
}
