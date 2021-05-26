package io.talken.dex.shared.exception;

/**
 * The type Transaction hash not match exception.
 */
public class TransactionHashNotMatchException extends DexException {
	private static final long serialVersionUID = -2038917617984952896L;

    /**
     * Instantiates a new Transaction hash not match exception.
     *
     * @param txHash the tx hash
     */
    public TransactionHashNotMatchException(String txHash) {
		super(DexExceptionTypeEnum.TX_HASH_NOT_MATCH, txHash);
	}

    /**
     * Instantiates a new Transaction hash not match exception.
     *
     * @param cause  the cause
     * @param txHash the tx hash
     */
    public TransactionHashNotMatchException(Throwable cause, String txHash) {
		super(cause, DexExceptionTypeEnum.TX_HASH_NOT_MATCH, txHash);
	}
}
