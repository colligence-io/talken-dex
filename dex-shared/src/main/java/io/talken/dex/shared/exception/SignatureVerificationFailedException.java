package io.talken.dex.shared.exception;

/**
 * The type Signature verification failed exception.
 */
public class SignatureVerificationFailedException extends DexException {
	private static final long serialVersionUID = -1358209060176335843L;

    /**
     * Instantiates a new Signature verification failed exception.
     *
     * @param org       the org
     * @param signature the signature
     */
    public SignatureVerificationFailedException(String org, String signature) {
		super(DexExceptionTypeEnum.SIGNATURE_VERIFICATION_FAILED, org, signature);
	}

    /**
     * Instantiates a new Signature verification failed exception.
     *
     * @param cause     the cause
     * @param org       the org
     * @param signature the signature
     */
    public SignatureVerificationFailedException(Throwable cause, String org, String signature) {
		super(cause, DexExceptionTypeEnum.SIGNATURE_VERIFICATION_FAILED, org, signature);
	}
}
