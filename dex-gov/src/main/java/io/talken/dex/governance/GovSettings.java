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

		getIntegration().setSignServer(new _Integration._SignServer());
		getIntegration().getSignServer().setAddr(secret.getSignServerAddr());
		getIntegration().getSignServer().setAppName(secret.getSignServerAppName());
		getIntegration().getSignServer().setAppKey(secret.getSignServerAppKey());

		//slack
		getIntegration().setSlack(secretReader.readSecret("slack", VaultSecretDataSlack.class));

		// coinmarketcap
		VaultSecretDataCoinMarketCap cmc_secret = secretReader.readSecret("coinmarketcap", VaultSecretDataCoinMarketCap.class);
		getIntegration().getCoinMarketCap().setApiKey(cmc_secret.getApiKey());
	}

	private String randomStringTable;

	private String talkDistributorAddress;

	private _Scheduler scheduler;

	@Getter
	@Setter
	public static class _Scheduler {
		private int poolSize;
		private int maxPoolSize;
		private int queueCapacity;
	}
}
