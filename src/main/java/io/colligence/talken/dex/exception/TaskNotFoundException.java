package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class TaskNotFoundException extends DexException {
	private static final long serialVersionUID = -1887057572019032174L;

	public TaskNotFoundException(String taskID) {
		super(DexExceptionType.TASK_NOT_FOUND, taskID);
	}
}
