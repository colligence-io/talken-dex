package io.talken.dex.exception;

public class StellarException extends DexException {
	private static final long serialVersionUID = 7617859538339055069L;

	public StellarException(Throwable cause, Object... args) {
		super(cause, DexExceptionTypeEnum.STELLAR_EXCEPTION, args);
	}
}
