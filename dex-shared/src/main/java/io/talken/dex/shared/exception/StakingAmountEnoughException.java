package io.talken.dex.shared.exception;

import java.math.BigDecimal;

public class StakingAmountEnoughException extends DexException {
	private static final long serialVersionUID = 330942829924574122L;

	public StakingAmountEnoughException(BigDecimal amountLimit, BigDecimal sumAmount) {
		super(DexExceptionTypeEnum.STAKING_AMOUNT_ENOUGH, amountLimit, sumAmount);
	}

	public StakingAmountEnoughException(Throwable cause, BigDecimal amountLimit, BigDecimal sumAmount) {
		super(cause, DexExceptionTypeEnum.STAKING_AMOUNT_ENOUGH, amountLimit, sumAmount);
	}
}
