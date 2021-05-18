package io.talken.dex.shared.exception;

/**
 * The type Transaction result processing exception.
 */
public class TransactionResultProcessingException extends DexException {
	private static final long serialVersionUID = -4348021278036277332L;

    /**
     * Instantiates a new Transaction result processing exception.
     *
     * @param taskId  the task id
     * @param message the message
     */
    public TransactionResultProcessingException(String taskId, String message) {
		super(DexExceptionTypeEnum.TRANSACTION_RESULT_PROCESSING_ERROR, taskId, message);
	}

    /**
     * Instantiates a new Transaction result processing exception.
     *
     * @param cause  the cause
     * @param taskId the task id
     */
    public TransactionResultProcessingException(Throwable cause, String taskId) {
		super(cause, DexExceptionTypeEnum.TRANSACTION_RESULT_PROCESSING_ERROR, taskId, cause.getClass().getSimpleName() + ":" + cause.getMessage());
	}
}
