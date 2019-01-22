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

	private _Server server;

	@Getter
	@Setter
	public static class _Server {
		private String wltAddress;
		private int wltPort;
		private String ancAddress;
		private int ancPort;
		private String masAddress;
		private int masPort;
		private String txtAddress;
		private int txtPort;
	}

	private _Fee fee;

	@Getter
	@Setter
	public static class _Fee {
		private double offerFeeRate;
		private double offerFeeRateForCTX;

		private String deanchorFeePivotAsset;
		private double deanchorFeeAmount;
	}

	// MAS mockup

	private List<_MasMock> masMockUp;

	@Getter
	@Setter
	public static class _MasMock {
		private String code;
		private String assetIssuer;
		private String assetBase;
		private List<String> assetHolder;
		private String offerFeeHolder;
		private String deanchorFeeHolder;
	}
}
