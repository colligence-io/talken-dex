package io.talken.dex.shared.exception;

public class StakingUserEnoughException extends DexException {
	private static final long serialVersionUID = 6052920387426056015L;

	public StakingUserEnoughException(int userLimit, int sumUser) {
		super(DexExceptionTypeEnum.STAKING_USER_ENOUGH, userLimit, sumUser);
	}

	public StakingUserEnoughException(Throwable cause, int userLimit, int sumUser) {
        super(cause, DexExceptionTypeEnum.STAKING_USER_ENOUGH, userLimit, sumUser);
	}
}
