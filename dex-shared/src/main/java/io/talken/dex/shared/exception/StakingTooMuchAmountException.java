package io.talken.dex.shared.exception;

import java.math.BigDecimal;

public class StakingTooMuchAmountException extends DexException {
	private static final long serialVersionUID = 6208235347894932471L;

	public StakingTooMuchAmountException(BigDecimal max, BigDecimal amount) {
		super(DexExceptionTypeEnum.STAKING_TOO_MUCH_AMOUNT, max, amount);
	}

	public StakingTooMuchAmountException(Throwable cause, BigDecimal max, BigDecimal amount) {
		super(cause, DexExceptionTypeEnum.STAKING_TOO_MUCH_AMOUNT, max, amount);
	}
}
