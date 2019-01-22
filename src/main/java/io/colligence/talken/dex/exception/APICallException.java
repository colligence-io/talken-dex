package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class APICallException extends DexException {
	private static final long serialVersionUID = -14601193174516052L;

	public APICallException(String apiName, String message) {
		super(DexExceptionType.APICALL_ERROR, message);
	}

	public APICallException(Throwable cause, String apiName) {
		super(cause, DexExceptionType.APICALL_ERROR, cause.getMessage());
	}

	public APICallException(Throwable cause, String apiName, String message) {
		super(cause, DexExceptionType.APICALL_ERROR, message);
	}
}
