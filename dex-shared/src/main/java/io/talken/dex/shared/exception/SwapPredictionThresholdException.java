package io.talken.dex.shared.exception;

import java.math.BigDecimal;

public class SwapPredictionThresholdException extends DexException {

	public SwapPredictionThresholdException(String sourceAssetCode, String targetAssetCode, BigDecimal requested, BigDecimal prediction) {
		super(DexExceptionTypeEnum.SWAP_PREDICTION_THRESHOLD, sourceAssetCode, targetAssetCode, requested, prediction);
	}

	public SwapPredictionThresholdException(Throwable cause, String sourceAssetCode, String targetAssetCode, BigDecimal requested, BigDecimal prediction) {
		super(cause, DexExceptionTypeEnum.SWAP_PREDICTION_THRESHOLD, sourceAssetCode, targetAssetCode, requested, prediction);
	}
}
