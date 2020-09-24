package io.talken.dex.shared.exception;

import java.math.BigDecimal;

public class StakingTooLittleAmountException extends DexException {
	private static final long serialVersionUID = 8627813631340358251L;

	public StakingTooLittleAmountException(BigDecimal min, BigDecimal amount) {
		super(DexExceptionTypeEnum.STAKING_TOO_LITTLE_AMOUNT, min, amount);
	}

	public StakingTooLittleAmountException(Throwable cause, BigDecimal min, BigDecimal amount) {
		super(cause, DexExceptionTypeEnum.STAKING_TOO_LITTLE_AMOUNT, min, amount);
	}
}
