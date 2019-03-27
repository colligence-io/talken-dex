package io.talken.dex.exception;

public class TransactionResultProcessingException extends DexException {
	private static final long serialVersionUID = -4348021278036277332L;

	public TransactionResultProcessingException(String taskId, String message) {
		super(DexExceptionTypeEnum.TRANSACTION_RESULT_PROCESSING_ERROR, taskId, message);
	}

	public TransactionResultProcessingException(Throwable cause, String taskId) {
		super(cause, DexExceptionTypeEnum.TRANSACTION_RESULT_PROCESSING_ERROR, taskId, cause.getClass().getSimpleName() + ":" + cause.getMessage());
	}
}
