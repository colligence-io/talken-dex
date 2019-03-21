package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class TokenMetaLoadException extends DexException {
	private static final long serialVersionUID = 5593557515932630983L;

	public TokenMetaLoadException(String message) {
		super(DexExceptionType.CANNOT_LOAD_TOKEN_META_DATA, message);
	}

	public TokenMetaLoadException(Throwable cause) {
		super(cause, DexExceptionType.CANNOT_LOAD_TOKEN_META_DATA, cause.getMessage());
	}

	public TokenMetaLoadException(Throwable cause, String message) {
		super(cause, DexExceptionType.CANNOT_LOAD_TOKEN_META_DATA, message);
	}
}
