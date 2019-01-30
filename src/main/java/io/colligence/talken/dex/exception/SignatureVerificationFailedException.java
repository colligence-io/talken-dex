package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class SignatureVerificationFailedException extends DexException {
	private static final long serialVersionUID = -1358209060176335843L;

	public SignatureVerificationFailedException(String org, String signature) {
		super(DexExceptionType.SIGNATURE_VERIFICATION_FAILED, org, signature);
	}

	public SignatureVerificationFailedException(Throwable cause, String org, String signature) {
		super(cause, DexExceptionType.SIGNATURE_VERIFICATION_FAILED, org, signature);
	}
}
