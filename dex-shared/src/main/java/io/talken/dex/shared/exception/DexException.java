package io.talken.dex.shared.exception;

import io.talken.common.exception.TalkenException;

/**
 * The type Dex exception.
 */
public abstract class DexException extends TalkenException {
	private static final long serialVersionUID = -3368616269685308480L;

	static {
		loadDefaultMessageFormats(DexExceptionTypeEnum.class, "i18n/dexException.properties");
	}

    /**
     * Instantiates a new Dex exception.
     *
     * @param type the type
     * @param args the args
     */
    protected DexException(ExceptionTypeEnum type, Object... args) {
		super(type, args);
	}

    /**
     * Instantiates a new Dex exception.
     *
     * @param cause the cause
     * @param type  the type
     * @param args  the args
     */
    protected DexException(Throwable cause, ExceptionTypeEnum type, Object... args) {
		super(cause, type, args);
	}
}
