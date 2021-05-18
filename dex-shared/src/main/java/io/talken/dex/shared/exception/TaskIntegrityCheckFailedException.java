package io.talken.dex.shared.exception;

/**
 * The type Task integrity check failed exception.
 */
public class TaskIntegrityCheckFailedException extends DexException {
	private static final long serialVersionUID = 3439112146505633353L;

    /**
     * Instantiates a new Task integrity check failed exception.
     *
     * @param taskID the task id
     */
    public TaskIntegrityCheckFailedException(String taskID) {
		super(DexExceptionTypeEnum.TASK_INTEGRITY_CHECK_FAILED, taskID);
	}
}
