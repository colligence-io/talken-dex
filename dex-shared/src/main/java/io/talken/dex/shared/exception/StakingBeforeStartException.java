package io.talken.dex.shared.exception;

import java.time.LocalDateTime;

/**
 * The type Staking before start exception.
 */
public class StakingBeforeStartException extends DexException {
	private static final long serialVersionUID = -27111925074117133L;

    /**
     * Instantiates a new Staking before start exception.
     *
     * @param stakingCode the staking code
     * @param symbol      the symbol
     * @param start       the start
     */
    public StakingBeforeStartException(String stakingCode, String symbol, LocalDateTime start) {
		super(DexExceptionTypeEnum.STAKING_BEFORE_START, stakingCode, symbol, start);
	}

    /**
     * Instantiates a new Staking before start exception.
     *
     * @param cause       the cause
     * @param stakingCode the staking code
     * @param symbol      the symbol
     * @param start       the start
     */
    public StakingBeforeStartException(Throwable cause, String stakingCode, String symbol, LocalDateTime start) {
		super(cause, DexExceptionTypeEnum.STAKING_BEFORE_START, stakingCode, symbol, start);
	}
}
