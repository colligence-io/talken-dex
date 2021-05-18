package io.talken.dex.shared.exception;

import java.math.BigDecimal;

/**
 * The type Staking amount enough exception.
 */
public class StakingAmountEnoughException extends DexException {
	private static final long serialVersionUID = 330942829924574122L;

    /**
     * Instantiates a new Staking amount enough exception.
     *
     * @param amountLimit the amount limit
     * @param sumAmount   the sum amount
     */
    public StakingAmountEnoughException(BigDecimal amountLimit, BigDecimal sumAmount) {
		super(DexExceptionTypeEnum.STAKING_AMOUNT_ENOUGH, amountLimit, sumAmount);
	}

    /**
     * Instantiates a new Staking amount enough exception.
     *
     * @param cause       the cause
     * @param amountLimit the amount limit
     * @param sumAmount   the sum amount
     */
    public StakingAmountEnoughException(Throwable cause, BigDecimal amountLimit, BigDecimal sumAmount) {
		super(cause, DexExceptionTypeEnum.STAKING_AMOUNT_ENOUGH, amountLimit, sumAmount);
	}
}
