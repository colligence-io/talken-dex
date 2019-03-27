package io.talken.dex.scheduler.txmonitor;

public class TaskTransactionProcessResult {

	private boolean isSuccess;
	private TaskTransactionProcessError error;

	public static TaskTransactionProcessResult success() {
		return new TaskTransactionProcessResult(true);
	}

	public static TaskTransactionProcessResult error(TaskTransactionProcessError error) {
		return new TaskTransactionProcessResult(false, error);
	}

	public static TaskTransactionProcessResult error(String code, Throwable exception) {
		return new TaskTransactionProcessResult(false, new TaskTransactionProcessError(code, exception, exception.getClass().getSimpleName() + " : " + exception.getMessage()));
	}

	private TaskTransactionProcessResult(boolean isSuccess) {
		this.isSuccess = isSuccess;
		this.error = null;
	}

	private TaskTransactionProcessResult(boolean isSuccess, TaskTransactionProcessError error) {
		this.isSuccess = isSuccess;
		this.error = error;
	}

	public boolean isSuccess() {
		return isSuccess;
	}

	public TaskTransactionProcessError getError() {
		return error;
	}
}
