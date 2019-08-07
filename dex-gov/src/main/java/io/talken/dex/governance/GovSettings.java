package io.talken.dex.governance;

import io.talken.common.persistence.vault.VaultSecretReader;
import io.talken.common.persistence.vault.data.VaultSecretDataCoinMarketCap;
import io.talken.common.persistence.vault.data.VaultSecretDataDexSettings;
import io.talken.common.persistence.vault.data.VaultSecretDataSlack;
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
public class GovSettings extends DexSettings {
	@Autowired
	private VaultSecretReader secretReader;

	@PostConstruct
	private void readVaultSecret() {
		// dex settings
		VaultSecretDataDexSettings secret = secretReader.readSecret("dexSettings", VaultSecretDataDexSettings.class);
		randomStringTable = secret.getTaskIdSeed();
		DexTaskId.init(randomStringTable);

		integration.signServer = new _Integration._SignServer();
		integration.signServer.addr = secret.getSignServerAddr();
		integration.signServer.appName = secret.getSignServerAppName();
		integration.signServer.appKey = secret.getSignServerAppKey();

		//slack
		integration.slack = secretReader.readSecret("slack", VaultSecretDataSlack.class);

		VaultSecretDataCoinMarketCap cmc_secret = secretReader.readSecret("coinmarketcap", VaultSecretDataCoinMarketCap.class);

		integration.coinMarketCap.apiKey = cmc_secret.getApiKey();
	}

	private String randomStringTable;

	private String rewardDistributorAddress;

	private _Scheduler scheduler;

	@Getter
	@Setter
	public static class _Scheduler {
		private int poolSize;
	}

	private _Integration integration;

	@Getter
	@Setter
	public static class _Integration {
		private VaultSecretDataSlack slack;
		private _CoinMarketCap coinMarketCap;
		private _SignServer signServer;
		private _Wallet wallet;

		@Getter
		@Setter
		public static class _Wallet {
			private String apiUrl;
		}

		@Getter
		@Setter
		public static class _SignServer {
			private String addr;
			private String appName;
			private String appKey;
		}

		@Getter
		@Setter
		public static class _CoinMarketCap {
			private String apiKey;
			private String latestUrl;
		}
	}
}
