package io.talken.dex.shared.exception;

/**
 * The type Account not found exception.
 */
public class AccountNotFoundException extends DexException {
	private static final long serialVersionUID = 4242100931916938003L;

    /**
     * Instantiates a new Account not found exception.
     *
     * @param type      the type
     * @param accountID the account id
     */
    public AccountNotFoundException(String type, String accountID) {
		super(DexExceptionTypeEnum.ACCOUNT_NOT_FOUND, type, accountID);
	}

    /**
     * Instantiates a new Account not found exception.
     *
     * @param cause     the cause
     * @param type      the type
     * @param accountID the account id
     */
    public AccountNotFoundException(Throwable cause, String type, String accountID) {
		super(cause, DexExceptionTypeEnum.ACCOUNT_NOT_FOUND, type, accountID);
	}
}
