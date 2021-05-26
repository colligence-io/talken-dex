package io.talken.dex.shared.exception;

import java.math.BigDecimal;

/**
 * The type Staking too over amount exception.
 */
public class StakingTooOverAmountException extends DexException {
	private static final long serialVersionUID = -7849858059331463158L;

    /**
     * Instantiates a new Staking too over amount exception.
     *
     * @param over the over
     */
    public StakingTooOverAmountException(BigDecimal over) {
		super(DexExceptionTypeEnum.STAKING_TOO_OVER_AMOUNT, over);
	}

    /**
     * Instantiates a new Staking too over amount exception.
     *
     * @param cause the cause
     * @param over  the over
     */
    public StakingTooOverAmountException(Throwable cause, BigDecimal over) {
		super(cause, DexExceptionTypeEnum.STAKING_TOO_OVER_AMOUNT, over);
	}
}
