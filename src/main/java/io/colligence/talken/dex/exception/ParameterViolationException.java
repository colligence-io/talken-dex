package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class ParameterViolationException extends DexException {
	private static final long serialVersionUID = 7493831309460983342L;

	public ParameterViolationException(String message) {
		super(DexExceptionType.PARAMETER_VIOLATION, message);
	}
}
