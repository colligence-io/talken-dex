package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class StellarAccountNotFoundException extends DexException {
	private static final long serialVersionUID = 4242100931916938003L;

	public StellarAccountNotFoundException(String type, String accountID) {
		super(DexExceptionType.ACCOUNT_NOT_FOUND, type, accountID);
	}

	public StellarAccountNotFoundException(Throwable cause, String type, String accountID) {
		super(cause, DexExceptionType.ACCOUNT_NOT_FOUND, type, accountID);
	}
}
