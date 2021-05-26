package io.talken.dex.shared.exception;

/**
 * The type Bctx exception.
 */
public class BctxException extends DexException {
	private static final long serialVersionUID = 6161809053200680035L;

    /**
     * Instantiates a new Bctx exception.
     *
     * @param code    the code
     * @param message the message
     */
    public BctxException(String code, String message) {
		super(DexExceptionTypeEnum.BCTX_EXCEPTION, code, message);
	}

    /**
     * Instantiates a new Bctx exception.
     *
     * @param cause   the cause
     * @param code    the code
     * @param message the message
     */
    public BctxException(Throwable cause, String code, String message) {
		super(cause, DexExceptionTypeEnum.BCTX_EXCEPTION, code, message);
	}
}
