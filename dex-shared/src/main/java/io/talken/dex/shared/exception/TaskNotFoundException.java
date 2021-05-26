package io.talken.dex.shared.exception;

/**
 * The type Task not found exception.
 */
public class TaskNotFoundException extends DexException {
	private static final long serialVersionUID = -1887057572019032174L;

    /**
     * Instantiates a new Task not found exception.
     *
     * @param taskID the task id
     */
    public TaskNotFoundException(String taskID) {
		super(DexExceptionTypeEnum.TASK_NOT_FOUND, taskID);
	}
}
