package io.talken.dex.shared.exception;

import java.math.BigDecimal;

/**
 * The type Staking balance not enough exception.
 */
public class StakingBalanceNotEnoughException extends DexException {
	private static final long serialVersionUID = 6208235347894932471L;

    /**
     * Instantiates a new Staking balance not enough exception.
     *
     * @param balance the balance
     * @param amount  the amount
     */
    public StakingBalanceNotEnoughException(BigDecimal balance, BigDecimal amount) {
		super(DexExceptionTypeEnum.STAKING_BALANCE_NOT_ENOUGH, balance, amount);
	}

    /**
     * Instantiates a new Staking balance not enough exception.
     *
     * @param cause   the cause
     * @param balance the balance
     * @param amount  the amount
     */
    public StakingBalanceNotEnoughException(Throwable cause, BigDecimal balance, BigDecimal amount) {
		super(cause, DexExceptionTypeEnum.STAKING_BALANCE_NOT_ENOUGH, balance, amount);
	}
}
