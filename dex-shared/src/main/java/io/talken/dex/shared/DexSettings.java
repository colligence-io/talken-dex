package io.talken.dex.shared;

import io.talken.common.persistence.vault.VaultSecretReader;
import io.talken.common.persistence.vault.data.VaultSecretDataLuniverse;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;

@Data
public class DexSettings {
	@Autowired
	private VaultSecretReader secretReader;

	@PostConstruct
	private void readVaultSecret() {
		this.getBcnode().getLuniverse().secret = secretReader.readSecret("luniverse", VaultSecretDataLuniverse.class);
	}

	private _Fee fee;

	@Getter
	@Setter
	public static class _Fee {
		private BigDecimal offerFeeRate;
		private BigDecimal offerFeeRateTalkFactor;

		private String deanchorFeePivotAsset;
		private BigDecimal deanchorFeeAmount;
		private BigDecimal deanchorFeeRateTalkFactor;

		private int refundRetryInterval;
		private int refundMaxRetry;
	}

	private _BCNodes bcnode;

	@Getter
	@Setter
	public static class _BCNodes {
		private NodeServerList stellar;
		private _Ethereum ethereum;
		private _Luniverse luniverse;
	}

	@Getter
	@Setter
	public static class NodeServerList {
		private String network;
		private List<String> serverList;
	}

	@Getter
	@Setter
	public static class _Ethereum extends NodeServerList {
		private String gasOracleUrl;
	}

	@Getter
	@Setter
	public static class _Luniverse {
		private VaultSecretDataLuniverse secret;
		private String apiUri;
		private String sideRpcUri;
		private String mainRpcUri;
		private String mtSymbol;
		private String stSymbol;
	}
}
