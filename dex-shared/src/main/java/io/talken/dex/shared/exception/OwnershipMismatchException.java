package io.talken.dex.shared.exception;

/**
 * The type Ownership mismatch exception.
 */
public class OwnershipMismatchException extends DexException {
	private static final long serialVersionUID = -8398781281735864893L;

    /**
     * Instantiates a new Ownership mismatch exception.
     *
     * @param message the message
     */
    public OwnershipMismatchException(String message) {
		super(DexExceptionTypeEnum.OWNERSHIP_MISMATCH, message);
	}
}
