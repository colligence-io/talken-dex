package io.talken.dex.shared.exception;

import io.talken.common.exception.TalkenException;

public abstract class DexException extends TalkenException {
	private static final long serialVersionUID = -3368616269685308480L;

	static {
		loadDefaultMessageFormats(DexExceptionTypeEnum.class, "i18n/dexException.properties");
	}

	protected DexException(ExceptionTypeEnum type, Object... args) {
		super(type, args);
	}

	protected DexException(Throwable cause, ExceptionTypeEnum type, Object... args) {
		super(cause, type, args);
	}
}
