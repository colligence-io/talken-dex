package io.talken.dex.api.exception;

public class InternalServerErrorException extends DexException {
	private static final long serialVersionUID = -4727089810718111421L;

	public InternalServerErrorException(Throwable cause) {
		super(cause, DexExceptionTypeEnum.INTERNAL_SERVER_ERROR, cause.getMessage());
	}

	public InternalServerErrorException(Throwable cause, String message) {
		super(cause, DexExceptionTypeEnum.INTERNAL_SERVER_ERROR, message);
	}
}
