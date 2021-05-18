package io.talken.dex.shared.exception;

/**
 * The type Pending last request exception.
 */
public class PendingLastRequestException extends DexException {
	private static final long serialVersionUID = -7846737325916872022L;

    /**
     * Instantiates a new Pending last request exception.
     */
    public PendingLastRequestException() {
		super(DexExceptionTypeEnum.PENDING_LAST_REQUEST);
	}

    /**
     * Instantiates a new Pending last request exception.
     *
     * @param cause the cause
     */
    public PendingLastRequestException(Throwable cause) {
		super(cause, DexExceptionTypeEnum.PENDING_LAST_REQUEST);
	}
}
