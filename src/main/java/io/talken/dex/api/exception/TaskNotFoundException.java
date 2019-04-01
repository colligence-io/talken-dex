package io.talken.dex.api.exception;

public class TaskNotFoundException extends DexException {
	private static final long serialVersionUID = -1887057572019032174L;

	public TaskNotFoundException(String taskID) {
		super(DexExceptionTypeEnum.TASK_NOT_FOUND, taskID);
	}
}
