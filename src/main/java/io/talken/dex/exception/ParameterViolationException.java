package io.talken.dex.exception;

public class ParameterViolationException extends DexException {
	private static final long serialVersionUID = 7493831309460983342L;

	public ParameterViolationException(String message) {
		super(DexExceptionTypeEnum.PARAMETER_VIOLATION, message);
	}
}
