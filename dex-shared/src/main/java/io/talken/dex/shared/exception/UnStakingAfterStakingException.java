package io.talken.dex.shared.exception;

/**
 * The type Un staking after staking exception.
 */
public class UnStakingAfterStakingException extends DexException {
	private static final long serialVersionUID = 2992406843517296624L;

    /**
     * Instantiates a new Un staking after staking exception.
     *
     * @param stakingCode the staking code
     * @param symbol      the symbol
     */
    public UnStakingAfterStakingException(String stakingCode, String symbol) {
		super(DexExceptionTypeEnum.UNSTAKING_AFTER_STAKING, stakingCode, symbol);
	}

    /**
     * Instantiates a new Un staking after staking exception.
     *
     * @param cause       the cause
     * @param stakingCode the staking code
     * @param symbol      the symbol
     */
    public UnStakingAfterStakingException(Throwable cause, String stakingCode, String symbol) {
		super(cause, DexExceptionTypeEnum.UNSTAKING_AFTER_STAKING, stakingCode, symbol);
	}
}
