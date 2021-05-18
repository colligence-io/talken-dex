package io.talken.dex.shared.exception;

/**
 * The type Signing exception.
 */
public class SigningException extends DexException {
	private static final long serialVersionUID = 8585903027424064353L;

	private String message;

    /**
     * Instantiates a new Signing exception.
     *
     * @param publicKey the public key
     * @param message   the message
     */
    public SigningException(String publicKey, String message) {
		super(DexExceptionTypeEnum.SIGNING_ERROR, publicKey, message);
		this.message = message;
	}

    /**
     * Instantiates a new Signing exception.
     *
     * @param cause     the cause
     * @param publicKey the public key
     * @param message   the message
     */
    public SigningException(Throwable cause, String publicKey, String message) {
		super(cause, DexExceptionTypeEnum.SIGNING_ERROR, publicKey, message);
		this.message = message;
	}

	@Override
	public String getMessage() {
		return this.message;
	}
}
