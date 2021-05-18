package io.talken.dex.shared.exception;

/**
 * The type Effective amount is negative exception.
 */
public class EffectiveAmountIsNegativeException extends DexException {
	private static final long serialVersionUID = 2205883571063344125L;

    /**
     * Instantiates a new Effective amount is negative exception.
     *
     * @param type     the type
     * @param required the required
     */
    public EffectiveAmountIsNegativeException(String type, String required) {
		super(DexExceptionTypeEnum.EFFECTIVE_AMOUNT_NEGATIVE, type, required);
	}

    /**
     * Instantiates a new Effective amount is negative exception.
     *
     * @param cause    the cause
     * @param type     the type
     * @param required the required
     */
    public EffectiveAmountIsNegativeException(Throwable cause, String type, String required) {
		super(cause, DexExceptionTypeEnum.EFFECTIVE_AMOUNT_NEGATIVE, type, required);
	}
}
