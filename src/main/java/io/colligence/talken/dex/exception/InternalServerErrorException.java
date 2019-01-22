package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class InternalServerErrorException extends DexException {
	private static final long serialVersionUID = -4727089810718111421L;

	public InternalServerErrorException(Throwable cause) {
		super(cause, DexExceptionType.INTERNAL_SERVER_ERROR, cause.getMessage());
	}

	public InternalServerErrorException(Throwable cause, String message) {
		super(cause, DexExceptionType.INTERNAL_SERVER_ERROR, message);
	}
}
