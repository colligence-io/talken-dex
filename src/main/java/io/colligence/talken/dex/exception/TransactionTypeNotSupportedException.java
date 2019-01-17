package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class TransactionTypeNotSupportedException extends DexException {
	private static final long serialVersionUID = 7593382434002475606L;

	public TransactionTypeNotSupportedException(Object... args) {
		super(DexExceptionType.TX_TYPE_NOT_SUPPORTED, args);
	}

	public TransactionTypeNotSupportedException(Throwable cause, Object... args) {
		super(cause, DexExceptionType.TX_TYPE_NOT_SUPPORTED, args);
	}
}
