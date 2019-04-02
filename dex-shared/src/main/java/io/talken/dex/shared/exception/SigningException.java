package io.talken.dex.shared.exception;

public class SigningException extends DexException {
	private static final long serialVersionUID = 8585903027424064353L;

	private String message;

	public SigningException(String publicKey, String message) {
		super(DexExceptionTypeEnum.SIGNING_ERROR, publicKey, message);
		this.message = message;
	}

	public SigningException(Throwable cause, String publicKey, String message) {
		super(cause, DexExceptionTypeEnum.SIGNING_ERROR, publicKey, message);
		this.message = message;
	}

	@Override
	public String getMessage() {
		return this.message;
	}
}
