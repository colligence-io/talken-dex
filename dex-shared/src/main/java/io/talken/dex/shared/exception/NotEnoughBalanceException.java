package io.talken.dex.shared.exception;

/**
 * The type Not enough balance exception.
 */
public class NotEnoughBalanceException extends DexException {
	private static final long serialVersionUID = 4373066228855431143L;

    /**
     * Instantiates a new Not enough balance exception.
     *
     * @param type     the type
     * @param required the required
     */
    public NotEnoughBalanceException(String type, String required) {
		super(DexExceptionTypeEnum.BALANCE_NOT_ENOUGH, type, required);
	}

    /**
     * Instantiates a new Not enough balance exception.
     *
     * @param cause    the cause
     * @param type     the type
     * @param required the required
     */
    public NotEnoughBalanceException(Throwable cause, String type, String required) {
		super(cause, DexExceptionTypeEnum.BALANCE_NOT_ENOUGH, type, required);
	}
}
