package io.talken.dex.shared.exception;

/**
 * The type Un staking disabled exception.
 */
public class UnStakingDisabledException extends DexException {
	private static final long serialVersionUID = -960072183515804045L;

    /**
     * Instantiates a new Un staking disabled exception.
     *
     * @param stakingCode the staking code
     * @param symbol      the symbol
     */
    public UnStakingDisabledException(String stakingCode, String symbol) {
		super(DexExceptionTypeEnum.UNSTAKING_DISABLED, stakingCode, symbol);
	}

    /**
     * Instantiates a new Un staking disabled exception.
     *
     * @param cause       the cause
     * @param stakingCode the staking code
     * @param symbol      the symbol
     */
    public UnStakingDisabledException(Throwable cause, String stakingCode, String symbol) {
		super(cause, DexExceptionTypeEnum.UNSTAKING_DISABLED, stakingCode, symbol);
	}
}
