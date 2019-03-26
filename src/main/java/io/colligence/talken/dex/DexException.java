package io.colligence.talken.dex;

import io.colligence.talken.common.exception.TalkenException;

public abstract class DexException extends TalkenException {
	private static final long serialVersionUID = -3368616269685308480L;

	protected DexException(TalkenException.ExceptionTypeEnum type, Object... args) {
		super(type, args);
	}

	protected DexException(Throwable cause, TalkenException.ExceptionTypeEnum type, Object... args) {
		super(cause, type, args);
	}
}
