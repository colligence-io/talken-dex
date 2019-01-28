package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class StellarException extends DexException {
	private static final long serialVersionUID = 7617859538339055069L;

	public StellarException(Throwable cause, Object... args) {
		super(cause, DexExceptionType.STELLAR_EXCEPTION, args);
	}
}
