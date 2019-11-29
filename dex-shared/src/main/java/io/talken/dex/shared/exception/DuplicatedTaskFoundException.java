package io.talken.dex.shared.exception;

public class DuplicatedTaskFoundException extends DexException {
	private static final long serialVersionUID = -5485503553297277400L;

	public DuplicatedTaskFoundException(String taskId) {
		super(DexExceptionTypeEnum.DUPLICATED_TASK_FOUND, taskId);
	}

	public DuplicatedTaskFoundException(Throwable cause, String taskId) {
		super(cause, DexExceptionTypeEnum.DUPLICATED_TASK_FOUND, taskId);
	}
}
