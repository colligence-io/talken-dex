package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class TransactionResultProcessingException extends DexException {
	private static final long serialVersionUID = -4348021278036277332L;

	public TransactionResultProcessingException(String taskId, String message) {
		super(DexExceptionType.TRANSACTION_RESULT_PROCESSING_ERROR, taskId, message);
	}

	public TransactionResultProcessingException(Throwable cause, String taskId) {
		super(cause, DexExceptionType.TRANSACTION_RESULT_PROCESSING_ERROR, taskId, cause.getClass().getSimpleName() + ":" + cause.getMessage());
	}
}
