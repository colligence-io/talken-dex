package io.talken.dex.shared.exception;

/**
 * The type Staking already exists exception.
 */
public class StakingAlreadyExistsException extends DexException {
	private static final long serialVersionUID = -8897572105300139483L;

    /**
     * Instantiates a new Staking already exists exception.
     *
     * @param stakingCode the staking code
     * @param symbol      the symbol
     */
    public StakingAlreadyExistsException(String stakingCode, String symbol) {
		super(DexExceptionTypeEnum.STAKING_ALREADY_EXISTS, stakingCode, symbol);
	}

    /**
     * Instantiates a new Staking already exists exception.
     *
     * @param cause       the cause
     * @param stakingCode the staking code
     * @param symbol      the symbol
     */
    public StakingAlreadyExistsException(Throwable cause, String stakingCode, String symbol) {
		super(cause, DexExceptionTypeEnum.STAKING_ALREADY_EXISTS, stakingCode, symbol);
	}
}
