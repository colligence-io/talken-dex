package io.talken.dex.shared.exception;

import java.time.LocalDateTime;

/**
 * The type Staking after end exception.
 */
public class StakingAfterEndException extends DexException {
	private static final long serialVersionUID = -7955544469017669450L;

    /**
     * Instantiates a new Staking after end exception.
     *
     * @param stakingCode the staking code
     * @param symbol      the symbol
     * @param end         the end
     */
    public StakingAfterEndException(String stakingCode, String symbol, LocalDateTime end) {
		super(DexExceptionTypeEnum.STAKING_AFTER_END, stakingCode, symbol, end);
	}

    /**
     * Instantiates a new Staking after end exception.
     *
     * @param cause       the cause
     * @param stakingCode the staking code
     * @param symbol      the symbol
     * @param end         the end
     */
    public StakingAfterEndException(Throwable cause, String stakingCode, String symbol, LocalDateTime end) {
		super(cause, DexExceptionTypeEnum.STAKING_AFTER_END, stakingCode, symbol, end);
	}
}
