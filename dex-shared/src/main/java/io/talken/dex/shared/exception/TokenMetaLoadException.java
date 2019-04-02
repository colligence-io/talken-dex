package io.talken.dex.shared.exception;

public class TokenMetaLoadException extends DexException {
	private static final long serialVersionUID = 5593557515932630983L;

	public TokenMetaLoadException(String message) {
		super(DexExceptionTypeEnum.CANNOT_LOAD_TOKEN_META_DATA, message);
	}

	public TokenMetaLoadException(Throwable cause) {
		super(cause, DexExceptionTypeEnum.CANNOT_LOAD_TOKEN_META_DATA, cause.getMessage());
	}

	public TokenMetaLoadException(Throwable cause, String message) {
		super(cause, DexExceptionTypeEnum.CANNOT_LOAD_TOKEN_META_DATA, message);
	}
}
