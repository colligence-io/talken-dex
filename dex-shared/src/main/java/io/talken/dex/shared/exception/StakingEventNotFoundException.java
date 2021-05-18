package io.talken.dex.shared.exception;

/**
 * The type Staking event not found exception.
 */
public class StakingEventNotFoundException extends DexException {
	private static final long serialVersionUID = -163911762562614712L;

    /**
     * Instantiates a new Staking event not found exception.
     *
     * @param stakingId the staking id
     */
    public StakingEventNotFoundException(long stakingId) {
        super(DexExceptionTypeEnum.STAKING_EVENT_NOT_FOUND, "(id : " + stakingId + ")");
    }

    /**
     * Instantiates a new Staking event not found exception.
     *
     * @param stakingCode the staking code
     * @param symbol      the symbol
     */
    public StakingEventNotFoundException(String stakingCode, String symbol) {
		super(DexExceptionTypeEnum.STAKING_EVENT_NOT_FOUND, "(code : " + stakingCode + "/" + symbol + ")");
	}

    /**
     * Instantiates a new Staking event not found exception.
     *
     * @param cause       the cause
     * @param stakingCode the staking code
     * @param symbol      the symbol
     */
    public StakingEventNotFoundException(Throwable cause, String stakingCode, String symbol) {
		super(cause, DexExceptionTypeEnum.STAKING_EVENT_NOT_FOUND, "(code : " + stakingCode + "/" + symbol + ")");
	}
}
