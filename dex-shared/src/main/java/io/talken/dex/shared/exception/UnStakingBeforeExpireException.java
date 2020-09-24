package io.talken.dex.shared.exception;

import java.time.LocalDateTime;

public class UnStakingBeforeExpireException extends DexException {
	private static final long serialVersionUID = 6788160589972850649L;

	public UnStakingBeforeExpireException(String stakingCode, String symbol, LocalDateTime expr) {
		super(DexExceptionTypeEnum.UNSTAKING_BEFORE_EXPIRE, stakingCode, symbol, expr);
	}

	public UnStakingBeforeExpireException(Throwable cause, String stakingCode, String symbol, LocalDateTime expr) {
		super(cause, DexExceptionTypeEnum.UNSTAKING_BEFORE_EXPIRE, stakingCode, symbol, expr);
	}
}
