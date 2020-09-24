package io.talken.dex.shared.exception;

public class UnStakingAfterStakingException extends DexException {
	private static final long serialVersionUID = 2992406843517296624L;

	public UnStakingAfterStakingException(String stakingCode, String symbol) {
		super(DexExceptionTypeEnum.UNSTAKING_AFTER_STAKING, stakingCode, symbol);
	}

	public UnStakingAfterStakingException(Throwable cause, String stakingCode, String symbol) {
		super(cause, DexExceptionTypeEnum.UNSTAKING_AFTER_STAKING, stakingCode, symbol);
	}
}
