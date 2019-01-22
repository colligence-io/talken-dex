package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class TransactionHashNotMatchException extends DexException {
	private static final long serialVersionUID = -2038917617984952896L;

	public TransactionHashNotMatchException(String txHash) {
		super(DexExceptionType.TX_HASH_NOT_MATCH, txHash);
	}

	public TransactionHashNotMatchException(Throwable cause, String txHash) {
		super(cause, DexExceptionType.TX_HASH_NOT_MATCH, txHash);
	}
}
