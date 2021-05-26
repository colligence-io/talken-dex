package io.talken.dex.shared.exception;

import java.math.BigDecimal;

/**
 * The type Staking too little amount exception.
 */
public class StakingTooLittleAmountException extends DexException {
	private static final long serialVersionUID = 8627813631340358251L;

    /**
     * Instantiates a new Staking too little amount exception.
     *
     * @param min    the min
     * @param amount the amount
     */
    public StakingTooLittleAmountException(BigDecimal min, BigDecimal amount) {
		super(DexExceptionTypeEnum.STAKING_TOO_LITTLE_AMOUNT, min, amount);
	}

    /**
     * Instantiates a new Staking too little amount exception.
     *
     * @param cause  the cause
     * @param min    the min
     * @param amount the amount
     */
    public StakingTooLittleAmountException(Throwable cause, BigDecimal min, BigDecimal amount) {
		super(cause, DexExceptionTypeEnum.STAKING_TOO_LITTLE_AMOUNT, min, amount);
	}
}
