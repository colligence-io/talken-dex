package io.talken.dex.shared.exception;

import java.math.BigDecimal;

public class UnStakingTooOverAmountException extends DexException {
	private static final long serialVersionUID = 4954319967261983229L;

	public UnStakingTooOverAmountException(BigDecimal over) {
		super(DexExceptionTypeEnum.UNSTAKING_TOO_OVER_AMOUNT, over);
	}

	public UnStakingTooOverAmountException(Throwable cause, BigDecimal over) {
		super(cause, DexExceptionTypeEnum.UNSTAKING_TOO_OVER_AMOUNT, over);
	}
}
