package io.talken.dex.shared.exception;

public class StakingEventNotFoundException extends DexException {
	private static final long serialVersionUID = -163911762562614712L;

	public StakingEventNotFoundException(String stakingCode, String symbol) {
		super(DexExceptionTypeEnum.STAKING_EVENT_NOT_FOUND, stakingCode, symbol);
	}

	public StakingEventNotFoundException(Throwable cause, String stakingCode, String symbol) {
		super(cause, DexExceptionTypeEnum.STAKING_EVENT_NOT_FOUND, stakingCode, symbol);
	}
}
