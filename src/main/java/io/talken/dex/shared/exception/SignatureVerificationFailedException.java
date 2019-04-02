package io.talken.dex.shared.exception;

public class SignatureVerificationFailedException extends DexException {
	private static final long serialVersionUID = -1358209060176335843L;

	public SignatureVerificationFailedException(String org, String signature) {
		super(DexExceptionTypeEnum.SIGNATURE_VERIFICATION_FAILED, org, signature);
	}

	public SignatureVerificationFailedException(Throwable cause, String org, String signature) {
		super(cause, DexExceptionTypeEnum.SIGNATURE_VERIFICATION_FAILED, org, signature);
	}
}
