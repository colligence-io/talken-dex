package io.talken.dex.shared;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
public class DexSettings {
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
	}

	@Getter
	@Setter
	public static class NodeServerList {
		private String network;
		private List<String> serverList;
	}
}
