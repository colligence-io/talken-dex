package io.talken.dex.api.scheduler.txmonitor;

import org.slf4j.helpers.MessageFormatter;

public class TaskTransactionProcessError extends RuntimeException {
	private static final long serialVersionUID = -7083397512485380153L;

	private String code;

	public TaskTransactionProcessError(String code) {
		super(code);
		this.code = code;
	}

	public TaskTransactionProcessError(String code, String message) {
		super(message);
		this.code = code;
	}

	public TaskTransactionProcessError(String code, String format, Object arg) {
		this(code, MessageFormatter.format(format, arg).getMessage());
	}

	public TaskTransactionProcessError(String code, String format, Object arg1, Object arg2) {
		this(code, MessageFormatter.format(format, arg1, arg2).getMessage());
	}

	public TaskTransactionProcessError(String code, String format, Object... args) {
		this(code, MessageFormatter.arrayFormat(format, args).getMessage());
	}

	public TaskTransactionProcessError(String code, Throwable ex) {
		super(ex.getClass().getSimpleName() + " : " + ex.getMessage(), ex);
		this.code = code;
	}

	public TaskTransactionProcessError(String code, Throwable ex, String message) {
		super(message + " [" + ex.getClass().getSimpleName() + " : " + ex.getMessage() + "]", ex);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
