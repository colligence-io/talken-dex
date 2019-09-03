package io.talken.dex.shared.exception;

import java.math.BigDecimal;

public class SwapPathNotAvailableException extends DexException {

	public SwapPathNotAvailableException(String sourceAssetCode, String targetAssetCode, BigDecimal amount) {
		super(DexExceptionTypeEnum.SWAP_PATH_NOT_AVAILABLE, sourceAssetCode, targetAssetCode, amount);
	}

	public SwapPathNotAvailableException(Throwable cause, String sourceAssetCode, String targetAssetCode, BigDecimal amount) {
		super(cause, DexExceptionTypeEnum.SWAP_PATH_NOT_AVAILABLE, sourceAssetCode, targetAssetCode, amount);
	}
}
