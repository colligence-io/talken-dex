package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class SigningException extends DexException {
	private static final long serialVersionUID = 8585903027424064353L;

	public SigningException(String publicKey, String message) {
		super(DexExceptionType.SIGNING_ERROR, publicKey, message);
	}

	public SigningException(Throwable cause, String publicKey, String message) {
		super(cause, DexExceptionType.SIGNING_ERROR, publicKey, message);
	}
}
