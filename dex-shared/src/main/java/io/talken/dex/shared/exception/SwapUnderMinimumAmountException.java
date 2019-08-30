package io.talken.dex.shared.exception;

import java.math.BigDecimal;

public class SwapUnderMinimumAmountException extends DexException {

	public SwapUnderMinimumAmountException(String sourceAssetCode, BigDecimal amount, BigDecimal minimum) {
		super(DexExceptionTypeEnum.SWAP_UNDER_MINIMUM_AMOUNT, sourceAssetCode, amount, minimum);
	}

	public SwapUnderMinimumAmountException(Throwable cause, String sourceAssetCode, BigDecimal amount, BigDecimal minimum) {
		super(cause, DexExceptionTypeEnum.SWAP_UNDER_MINIMUM_AMOUNT, sourceAssetCode, amount, minimum);
	}
}
