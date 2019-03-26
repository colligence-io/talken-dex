package io.colligence.talken.dex.exception;

public class TaskIntegrityCheckFailedException extends DexException {
	private static final long serialVersionUID = 3439112146505633353L;

	public TaskIntegrityCheckFailedException(String taskID) {
		super(DexExceptionTypeEnum.TASK_INTEGRITY_CHECK_FAILED, taskID);
	}
}
