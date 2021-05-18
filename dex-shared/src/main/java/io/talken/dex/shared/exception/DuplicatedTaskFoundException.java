package io.talken.dex.shared.exception;

/**
 * The type Duplicated task found exception.
 */
public class DuplicatedTaskFoundException extends DexException {
	private static final long serialVersionUID = -5485503553297277400L;

    /**
     * Instantiates a new Duplicated task found exception.
     *
     * @param taskId the task id
     */
    public DuplicatedTaskFoundException(String taskId) {
		super(DexExceptionTypeEnum.DUPLICATED_TASK_FOUND, taskId);
	}

    /**
     * Instantiates a new Duplicated task found exception.
     *
     * @param cause  the cause
     * @param taskId the task id
     */
    public DuplicatedTaskFoundException(Throwable cause, String taskId) {
		super(cause, DexExceptionTypeEnum.DUPLICATED_TASK_FOUND, taskId);
	}
}
