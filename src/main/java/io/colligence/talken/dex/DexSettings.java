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
	private String randomStringTable;

	@Getter
	@Setter
	public static class _AccessToken {
		private String tokenHeader;
		private String jwtSecret;
		private int jwtExpiration;
	}

	private _Scheduler scheduler;

	@Getter
	@Setter
	public static class _Scheduler {
		private int poolSize;
	}

	private _AccessToken accessToken;

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
		private String rlyAddress;
		private String wltAddress;
		private String ancAddress;
		private String txtAddress;
		private String txtServerId;
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
	}
}
