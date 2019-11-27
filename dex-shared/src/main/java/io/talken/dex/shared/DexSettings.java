package io.talken.dex.shared;

import io.talken.common.persistence.vault.VaultSecretReader;
import io.talken.common.persistence.vault.data.VaultSecretDataDexSettings;
import io.talken.common.persistence.vault.data.VaultSecretDataLuniverse;
import io.talken.common.persistence.vault.data.VaultSecretDataSlack;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class DexSettings {
	@Autowired
	private VaultSecretReader secretReader;

	@PostConstruct
	private void readVaultSecret() {
		// dex settings
		VaultSecretDataDexSettings secret = secretReader.readSecret("dexSettings", VaultSecretDataDexSettings.class);
		DexTaskId.init(secret.getTaskIdSeed());
		getIntegration().setSignServer(new _Integration._SignServer());
		getIntegration().getSignServer().setAddr(secret.getSignServerAddr());
		getIntegration().getSignServer().setAppName(secret.getSignServerAppName());
		getIntegration().getSignServer().setAppKey(secret.getSignServerAppKey());

		//slack
		getIntegration().setSlack(secretReader.readSecret("slack", VaultSecretDataSlack.class));

		this.getBcnode().getLuniverse().secret = secretReader.readSecret("luniverse", VaultSecretDataLuniverse.class);
	}

	private _TradeWallet tradeWallet;

	@Getter
	@Setter
	public static class _TradeWallet {
		private String creatorAddress;
	}

	private _Task task;

	@Getter
	@Setter
	public static class _Task {
		private _CreateOffer createOffer;
		private _Deanchor deanchor;
		private _Swap swap;

		@Getter
		@Setter
		public static class _CreateOffer {
			private BigDecimal feeRate;
			private BigDecimal feeRateTalkFactor;
		}

		@Getter
		@Setter
		public static class _Deanchor {
			private BigDecimal feeAmountTalk;
		}

		@Getter
		@Setter
		public static class _Swap {
			private Map<String, _SwapFee> asset;

			@Getter
			@Setter
			public static class _SwapFee {
				private BigDecimal feeRate;
				private BigDecimal feeMaximum;
				private BigDecimal feeMinimum;
			}
		}
	}

	private _BCNodes bcnode;

	@Getter
	@Setter
	public static class _BCNodes {
		private _Stellar stellar;
		private _Ethereum ethereum;
		private _Luniverse luniverse;
	}

	@Getter
	@Setter
	public static class _Stellar {
		private String network;
		private String rpcUri;
		private List<_Channel> channels;

		@Getter
		@Setter
		public static class _Channel {
			private String publicKey;
			private String secretKey;
		}
	}

	@Getter
	@Setter
	public static class _Ethereum {
		private String network;
		private String rpcUri;
		private String infuraUri;
		private String gasOracleUrl;
	}

	@Getter
	@Setter
	public static class _Luniverse {
		private VaultSecretDataLuniverse secret;
		private String apiUri;
		private String sideRpcUri;
		private String mainRpcUri;
	}

	private _Integration integration;

	@Getter
	@Setter
	public static class _Integration {
		private VaultSecretDataSlack slack;
		private _CoinMarketCap coinMarketCap;
		private _SignServer signServer;
		private _Wallet wallet;
		private _Relay relay;
		private _Anchor anchor;

		@Getter
		@Setter
		public static class _Wallet {
			private String apiUrl;
		}

		@Getter
		@Setter
		public static class _Relay {
			private String apiUrl;
		}

		@Getter
		@Setter
		public static class _Anchor {
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
