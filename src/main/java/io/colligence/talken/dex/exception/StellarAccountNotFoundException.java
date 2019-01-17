package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class StellarAccountNotFoundException extends DexException {
	private static final long serialVersionUID = 4242100931916938003L;

	public StellarAccountNotFoundException(Object... args) {
		super(DexExceptionType.ACCOUNT_NOT_FOUND, args);
	}

	public StellarAccountNotFoundException(Throwable cause, Object... args) {
		super(cause, DexExceptionType.ACCOUNT_NOT_FOUND, args);
	}
}
