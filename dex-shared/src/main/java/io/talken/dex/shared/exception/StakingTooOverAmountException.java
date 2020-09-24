package io.talken.dex.shared.exception;

import java.math.BigDecimal;

public class StakingTooOverAmountException extends DexException {
	private static final long serialVersionUID = -7849858059331463158L;

	public StakingTooOverAmountException(BigDecimal over) {
		super(DexExceptionTypeEnum.STAKING_TOO_OVER_AMOUNT, over);
	}

	public StakingTooOverAmountException(Throwable cause, BigDecimal over) {
		super(cause, DexExceptionTypeEnum.STAKING_TOO_OVER_AMOUNT, over);
	}
}
