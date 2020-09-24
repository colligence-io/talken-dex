package io.talken.dex.shared.exception;

public class StakingAlreadyExistsException extends DexException {
	private static final long serialVersionUID = -8897572105300139483L;

	public StakingAlreadyExistsException(String stakingCode, String symbol) {
		super(DexExceptionTypeEnum.STAKING_ALREADY_EXISTS, stakingCode, symbol);
	}

	public StakingAlreadyExistsException(Throwable cause, String stakingCode, String symbol) {
		super(cause, DexExceptionTypeEnum.STAKING_ALREADY_EXISTS, stakingCode, symbol);
	}
}
