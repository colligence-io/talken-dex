package io.talken.dex.shared.exception;

import java.math.BigDecimal;

/**
 * The type Un staking too over amount exception.
 */
public class UnStakingTooOverAmountException extends DexException {
	private static final long serialVersionUID = 4954319967261983229L;

    /**
     * Instantiates a new Un staking too over amount exception.
     *
     * @param over the over
     */
    public UnStakingTooOverAmountException(BigDecimal over) {
		super(DexExceptionTypeEnum.UNSTAKING_TOO_OVER_AMOUNT, over);
	}

    /**
     * Instantiates a new Un staking too over amount exception.
     *
     * @param cause the cause
     * @param over  the over
     */
    public UnStakingTooOverAmountException(Throwable cause, BigDecimal over) {
		super(cause, DexExceptionTypeEnum.UNSTAKING_TOO_OVER_AMOUNT, over);
	}
}
