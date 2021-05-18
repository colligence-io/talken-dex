package io.talken.dex.shared.exception;

/**
 * The type Internal server error exception.
 */
public class InternalServerErrorException extends DexException {
	private static final long serialVersionUID = -4727089810718111421L;

    /**
     * Instantiates a new Internal server error exception.
     *
     * @param cause the cause
     */
    public InternalServerErrorException(Throwable cause) {
		super(cause, DexExceptionTypeEnum.INTERNAL_SERVER_ERROR, cause.getMessage());
	}

    /**
     * Instantiates a new Internal server error exception.
     *
     * @param cause   the cause
     * @param message the message
     */
    public InternalServerErrorException(Throwable cause, String message) {
		super(cause, DexExceptionTypeEnum.INTERNAL_SERVER_ERROR, message);
	}
}
