package io.talken.dex.shared.exception;

import java.math.BigDecimal;

/**
 * The type Staking too much amount exception.
 */
public class StakingTooMuchAmountException extends DexException {
	private static final long serialVersionUID = 6208235347894932471L;

    /**
     * Instantiates a new Staking too much amount exception.
     *
     * @param max    the max
     * @param amount the amount
     */
    public StakingTooMuchAmountException(BigDecimal max, BigDecimal amount) {
		super(DexExceptionTypeEnum.STAKING_TOO_MUCH_AMOUNT, max, amount);
	}

    /**
     * Instantiates a new Staking too much amount exception.
     *
     * @param cause  the cause
     * @param max    the max
     * @param amount the amount
     */
    public StakingTooMuchAmountException(Throwable cause, BigDecimal max, BigDecimal amount) {
		super(cause, DexExceptionTypeEnum.STAKING_TOO_MUCH_AMOUNT, max, amount);
	}
}
