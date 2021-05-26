package io.talken.dex.shared;

import io.talken.common.persistence.vault.VaultSecretReader;
import io.talken.common.persistence.vault.data.VaultSecretDataDexSettings;
import io.talken.common.persistence.vault.data.VaultSecretDataLuniverse;
import io.talken.common.persistence.vault.data.VaultSecretDataSlack;
import io.talken.common.persistence.vault.data.VaultSecretDataStellar;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.Map;

/**
 * The type Dex settings.
 */
@Data
public class DexSettings {
	@Autowired
	private VaultSecretReader secretReader;

    /**
     * The constant PIVOT_ASSET_CODE.
     */
    public static String PIVOT_ASSET_CODE = "USDT";

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

		// stellar
		this.getBcnode().getStellar().secret = secretReader.readSecret("stellar", VaultSecretDataStellar.class);
	}

	private _MaM mam;

    /**
     * The type Ma m.
     */
    @Getter
	@Setter
	public static class _MaM {
		private BigDecimal creatorMinBalance;
		private BigDecimal channelMinBalance;
		private BigDecimal issuerMinBalance;
		private _NetworkFeeBuffer netfeeBuffer;

        /**
         * The type Network fee buffer.
         */
        @Getter
		@Setter
		public static class _NetworkFeeBuffer {
			private Map<String, BigDecimal> holder;
			private Map<String, BigDecimal> distributor;
		}
	}

	private _TradeWallet tradeWallet;

    /**
     * The type Trade wallet.
     */
    @Getter
	@Setter
	public static class _TradeWallet {
		private String creatorAddress;
	}

	private _Task task;

    /**
     * The type Task.
     */
    @Getter
	@Setter
	public static class _Task {
		private _CreateOffer createOffer;
		private _Deanchor deanchor;

        /**
         * The type Create offer.
         */
        @Getter
		@Setter
		public static class _CreateOffer {
			private BigDecimal feeRatePivot;
		}

        /**
         * The type Deanchor.
         */
        @Getter
		@Setter
		public static class _Deanchor {
			private BigDecimal feeAmountTalk;
		}
	}

	private _BCNodes bcnode;

    /**
     * The type Bc nodes.
     */
    @Getter
	@Setter
	public static class _BCNodes {
		private _Stellar stellar;
		private _Ethereum ethereum;
		private _Luniverse luniverse;
        private _Klaytn klaytn;
        private _Bsc bsc;
		private _Filecoin filecoin;
		private _Heco heco;
	}

    /**
     * The type Filecoin.
     */
    @Getter
	@Setter
	public static class _Filecoin {
		private String infuraUri;
		private String projectId;
		private String projectSecret;
	}

    /**
     * The type Stellar.
     */
    @Getter
	@Setter
	public static class _Stellar {
		private String network;
		private String rpcUri;
		private String publicRpcUri;
		private VaultSecretDataStellar secret;
	}

    /**
     * The type Ethereum.
     */
    @Getter
	@Setter
	public static class _Ethereum {
		private String network;
		private String rpcUri;
		private String infuraUri;
		private String gasOracleUrl;
        private String gasEtherscanUrl;
	}

    /**
     * The type Luniverse.
     */
    @Getter
	@Setter
	public static class _Luniverse {
		private VaultSecretDataLuniverse secret;
		private String apiUri;
		private String sideRpcUri;
		private String mainRpcUri;
	}

    /**
     * The type Klaytn.
     */
    @Getter
    @Setter
    public static class _Klaytn {
        private int chainId;
        private String accessKeyId;
        private String secretAccessKey;
    }

    /**
     * The type Bsc.
     */
    @Getter
    @Setter
    public static class _Bsc {
        private int chainId;
        private String mainRpcUri;
    }

    /**
     * The type Heco.
     */
    @Getter
	@Setter
	public static class _Heco {
		private int chainId;
		private String mainRpcUri;
	}

	private _Integration integration;

    /**
     * The type Integration.
     */
    @Getter
	@Setter
	public static class _Integration {
		private VaultSecretDataSlack slack;
		private _CoinMarketCap coinMarketCap;
		private _SignServer signServer;
		private _Wallet wallet;
		private _Relay relay;
		private _Anchor anchor;

        /**
         * The type Wallet.
         */
        @Getter
		@Setter
		public static class _Wallet {
			private String apiUrl;
		}

        /**
         * The type Relay.
         */
        @Getter
		@Setter
		public static class _Relay {
			private String apiUrl;
		}

        /**
         * The type Anchor.
         */
        @Getter
		@Setter
		public static class _Anchor {
			private String apiUrl;
		}

        /**
         * The type Sign server.
         */
        @Getter
		@Setter
		public static class _SignServer {
			private String addr;
			private String appName;
			private String appKey;
		}

        /**
         * The type Coin market cap.
         */
        @Getter
		@Setter
		public static class _CoinMarketCap {
			private String apiKey;
			private String cryptoCurrencyUrl;
            private String globalMetricUrl;
		}
	}
}
