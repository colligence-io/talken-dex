package io.talken.dex.shared.exception;

/**
 * The type Staking user enough exception.
 */
public class StakingUserEnoughException extends DexException {
	private static final long serialVersionUID = 6052920387426056015L;

    /**
     * Instantiates a new Staking user enough exception.
     *
     * @param userLimit the user limit
     * @param sumUser   the sum user
     */
    public StakingUserEnoughException(int userLimit, int sumUser) {
		super(DexExceptionTypeEnum.STAKING_USER_ENOUGH, userLimit, sumUser);
	}

    /**
     * Instantiates a new Staking user enough exception.
     *
     * @param cause     the cause
     * @param userLimit the user limit
     * @param sumUser   the sum user
     */
    public StakingUserEnoughException(Throwable cause, int userLimit, int sumUser) {
        super(cause, DexExceptionTypeEnum.STAKING_USER_ENOUGH, userLimit, sumUser);
	}
}
