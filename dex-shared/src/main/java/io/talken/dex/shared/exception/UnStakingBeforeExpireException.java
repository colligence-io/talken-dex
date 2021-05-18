package io.talken.dex.shared.exception;

import java.time.LocalDateTime;

/**
 * The type Un staking before expire exception.
 */
public class UnStakingBeforeExpireException extends DexException {
	private static final long serialVersionUID = 6788160589972850649L;

    /**
     * Instantiates a new Un staking before expire exception.
     *
     * @param stakingCode the staking code
     * @param symbol      the symbol
     * @param expr        the expr
     */
    public UnStakingBeforeExpireException(String stakingCode, String symbol, LocalDateTime expr) {
		super(DexExceptionTypeEnum.UNSTAKING_BEFORE_EXPIRE, stakingCode, symbol, expr);
	}

    /**
     * Instantiates a new Un staking before expire exception.
     *
     * @param cause       the cause
     * @param stakingCode the staking code
     * @param symbol      the symbol
     * @param expr        the expr
     */
    public UnStakingBeforeExpireException(Throwable cause, String stakingCode, String symbol, LocalDateTime expr) {
		super(cause, DexExceptionTypeEnum.UNSTAKING_BEFORE_EXPIRE, stakingCode, symbol, expr);
	}
}
