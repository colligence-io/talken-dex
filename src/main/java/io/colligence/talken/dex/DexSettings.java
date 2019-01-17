package io.colligence.talken.dex;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties("talken.dex")
@Getter
@Setter
public class DexSettings {
	private _Stellar stellar;

	@Getter
	@Setter
	public static class _Stellar {
		private String network;
		private List<String> serverList;
	}

	private _Fee fee;

	@Getter
	@Setter
	public static class _Fee {
		private String offerFeeHolderAccount;
		private double offerFeeRate;
		private double offerFeeRateForCTX;

		private String deanchorFeeHolderAccount;
		private String deanchorFeePivotAsset;
		private double deanchorFeeAmount;
	}

	private List<_AssetType> assetTypeList;

	@Getter
	@Setter
	public static class _AssetType {
		private String code;
		private String issuer;
	}
}
