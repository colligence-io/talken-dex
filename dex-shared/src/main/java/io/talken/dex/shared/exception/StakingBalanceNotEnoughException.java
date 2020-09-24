package io.talken.dex.shared.exception;

import java.math.BigDecimal;

public class StakingBalanceNotEnoughException extends DexException {
	private static final long serialVersionUID = 6208235347894932471L;

	public StakingBalanceNotEnoughException(BigDecimal balance, BigDecimal amount) {
		super(DexExceptionTypeEnum.STAKING_BALANCE_NOT_ENOUGH, balance, amount);
	}

	public StakingBalanceNotEnoughException(Throwable cause, BigDecimal balance, BigDecimal amount) {
		super(cause, DexExceptionTypeEnum.STAKING_BALANCE_NOT_ENOUGH, balance, amount);
	}
}
