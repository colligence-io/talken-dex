package io.talken.dex.shared.exception;

/**
 * The type Parameter violation exception.
 */
public class ParameterViolationException extends DexException {
	private static final long serialVersionUID = 7493831309460983342L;

    /**
     * Instantiates a new Parameter violation exception.
     *
     * @param message the message
     */
    public ParameterViolationException(String message) {
		super(DexExceptionTypeEnum.PARAMETER_VIOLATION, message);
	}
}
