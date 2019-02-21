package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class TokenMetaDataNotFoundException extends DexException {
	private static final long serialVersionUID = -3337473019249235242L;

	String arg;

	public TokenMetaDataNotFoundException(String arg) {
		super(DexExceptionType.TOKEN_META_DATA_NOT_FOUND, arg);
		this.arg = arg;
	}

	public TokenMetaDataNotFoundException(Throwable cause, String arg) {
		super(cause, DexExceptionType.TOKEN_META_DATA_NOT_FOUND, arg);
		this.arg = arg;
	}

	public String getArg() {
		return arg;
	}
}
