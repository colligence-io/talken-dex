package io.colligence.talken.dex.exception;

public class TransactionHashNotMatchException extends DexException {
	private static final long serialVersionUID = -2038917617984952896L;

	public TransactionHashNotMatchException(String txHash) {
		super(DexExceptionTypeEnum.TX_HASH_NOT_MATCH, txHash);
	}

	public TransactionHashNotMatchException(Throwable cause, String txHash) {
		super(cause, DexExceptionTypeEnum.TX_HASH_NOT_MATCH, txHash);
	}
}
