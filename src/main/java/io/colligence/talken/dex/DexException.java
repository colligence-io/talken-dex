package io.colligence.talken.dex;

import io.colligence.talken.common.CLGException;

public abstract class DexException extends CLGException {
	private static final long serialVersionUID = -3368616269685308480L;

	protected DexException(ExceptionType type, Object... args) {
		super(type, args);
	}

	protected DexException(Throwable cause, ExceptionType type, Object... args) {
		super(cause, type, args);
	}
}
