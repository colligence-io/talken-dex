package io.talken.dex.shared.exception;

import java.time.LocalDateTime;

public class StakingAfterEndException extends DexException {
	private static final long serialVersionUID = -7955544469017669450L;

	public StakingAfterEndException(String stakingCode, String symbol, LocalDateTime end) {
		super(DexExceptionTypeEnum.STAKING_AFTER_END, stakingCode, symbol, end);
	}

	public StakingAfterEndException(Throwable cause, String stakingCode, String symbol, LocalDateTime end) {
		super(cause, DexExceptionTypeEnum.STAKING_AFTER_END, stakingCode, symbol, end);
	}
}
