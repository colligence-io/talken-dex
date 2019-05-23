package io.talken.dex.shared;

import io.talken.common.persistence.vault.VaultSecretReader;
import io.talken.common.persistence.vault.data.VaultSecretDataLuniverse;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class DexSettings {
	@Autowired
	private VaultSecretReader secretReader;

	@PostConstruct
	private void readVaultSecret() {
		this.getBcnode().getLuniverse().aux = new HashMap<>();

		VaultSecretDataLuniverse secret = secretReader.readSecret("luniverse", VaultSecretDataLuniverse.class);
		this.getBcnode().getLuniverse().aux.put(VaultSecretDataLuniverse.AUXKEY_APIKEY, secret.getApiKey());
		this.getBcnode().getLuniverse().aux.put(VaultSecretDataLuniverse.AUXKEY_ISSUER, secret.getCompanyWallet());
		this.getBcnode().getLuniverse().aux.put(VaultSecretDataLuniverse.AUXKEY_PRIVATEKEY, secret.getCompanyWalletPrivateKey());
		this.getBcnode().getLuniverse().aux.put(VaultSecretDataLuniverse.AUXKEY_POINTBASE, secret.getTalkp_base());
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

	private _BCNodes bcnode;

	@Getter
	@Setter
	public static class _BCNodes {
		private NodeServerList stellar;
		private NodeServerList ethereum;
		private NodeServerList luniverse;
	}

	@Getter
	@Setter
	public static class NodeServerList {
		private String network;
		private List<String> serverList;
		private Map<String, String> aux;
	}
}
