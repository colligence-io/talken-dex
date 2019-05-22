package io.talken.dex.governance;

import io.talken.common.persistence.vault.VaultSecretReader;
import io.talken.common.persistence.vault.data.VaultSecretDataDexSettings;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.DexTaskId;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
@ConfigurationProperties("talken.dex")
@Getter
@Setter
public class GovSettings extends DexSettings {
	@Autowired
	private VaultSecretReader secretReader;

	@PostConstruct
	private void readVaultSecret() {
		VaultSecretDataDexSettings secret = secretReader.readSecret("dexSettings", VaultSecretDataDexSettings.class);
		randomStringTable = secret.getTaskIdSeed();
		DexTaskId.init(randomStringTable);

		signServer = new _SignServer();
		signServer.addr = secret.getSignServerAddr();
		signServer.appName = secret.getSignServerAppName();
		signServer.appKey = secret.getSignServerAppKey();
	}

	private String randomStringTable;

	private _SignServer signServer;

	@Getter
	@Setter
	public static class _SignServer {
		private String addr;
		private String appName;
		private String appKey;
	}

	private _Scheduler scheduler;

	@Getter
	@Setter
	public static class _Scheduler {
		private int poolSize;
	}
}
