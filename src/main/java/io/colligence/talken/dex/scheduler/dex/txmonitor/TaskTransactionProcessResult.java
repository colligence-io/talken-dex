package io.colligence.talken.dex.scheduler.dex.txmonitor;

import org.slf4j.helpers.MessageFormatter;

public class TaskTransactionProcessResult {

	private boolean isSuccess;
	private String message;
	private Throwable cause;

	public TaskTransactionProcessResult(boolean isSuccess, String message) {
		this.isSuccess = isSuccess;
		this.message = message;
	}

	public TaskTransactionProcessResult() {
		this(true, "OK");
	}

	public TaskTransactionProcessResult(String message) {
		this(false, message);
	}

	public TaskTransactionProcessResult(String format, Object arg) {
		this(MessageFormatter.format(format, arg).getMessage());
	}

	public TaskTransactionProcessResult(String format, Object arg1, Object arg2) {
		this(MessageFormatter.format(format, arg1, arg2).getMessage());
	}

	public TaskTransactionProcessResult(String format, Object... args) {
		this(MessageFormatter.arrayFormat(format, args).getMessage());
	}

	public TaskTransactionProcessResult(Throwable ex) {
		this(ex.getClass().getSimpleName() + " occured : " + ex.getMessage());
		this.cause = ex;
	}

	public TaskTransactionProcessResult(Throwable ex, String format, Object... args) {
		this(format, args);
		this.cause = ex;
	}

	public boolean isSuccess() {
		return isSuccess;
	}

	public String getMessage() {
		return message;
	}

	public Throwable getCause() {
		return cause;
	}
}
