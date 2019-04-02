package io.talken.dex.shared.exception;

public class AccountNotFoundException extends DexException {
	private static final long serialVersionUID = 4242100931916938003L;

	public AccountNotFoundException(String type, String accountID) {
		super(DexExceptionTypeEnum.ACCOUNT_NOT_FOUND, type, accountID);
	}

	public AccountNotFoundException(Throwable cause, String type, String accountID) {
		super(cause, DexExceptionTypeEnum.ACCOUNT_NOT_FOUND, type, accountID);
	}
}
