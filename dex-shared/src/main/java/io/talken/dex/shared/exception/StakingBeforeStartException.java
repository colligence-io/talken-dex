package io.talken.dex.shared.exception;

import java.time.LocalDateTime;

public class StakingBeforeStartException extends DexException {
	private static final long serialVersionUID = -27111925074117133L;

	public StakingBeforeStartException(String stakingCode, String symbol, LocalDateTime start) {
		super(DexExceptionTypeEnum.STAKING_BEFORE_START, stakingCode, symbol, start);
	}

	public StakingBeforeStartException(Throwable cause, String stakingCode, String symbol, LocalDateTime start) {
		super(cause, DexExceptionTypeEnum.STAKING_BEFORE_START, stakingCode, symbol, start);
	}
}
