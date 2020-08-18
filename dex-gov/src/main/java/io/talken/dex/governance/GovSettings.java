package io.talken.dex.governance;

import io.talken.common.persistence.vault.VaultSecretReader;
import io.talken.common.persistence.vault.data.VaultSecretDataCoinMarketCap;
import io.talken.dex.shared.DexSettings;
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
		// coinmarketcap
		VaultSecretDataCoinMarketCap cmc_secret = secretReader.readSecret("coinmarketcap", VaultSecretDataCoinMarketCap.class);
		getIntegration().getCoinMarketCap().setApiKey(cmc_secret.getApiKey());
	}

	private _Scheduler scheduler;


	@Getter
	@Setter
	public static class _Scheduler {
		private int poolSize;
		private int maxPoolSize;
		private int queueCapacity;
	}
}
