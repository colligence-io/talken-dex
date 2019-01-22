package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class TaskIntegrityCheckFailedException extends DexException {
	private static final long serialVersionUID = 3439112146505633353L;

	public TaskIntegrityCheckFailedException(String taskID) {
		super(DexExceptionType.TASK_INTEGRITY_CHECK_FAILED, taskID);
	}
}
