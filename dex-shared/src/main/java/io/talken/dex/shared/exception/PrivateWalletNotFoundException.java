package io.talken.dex.shared.exception;

/**
 * The type Private wallet not found exception.
 */
public class PrivateWalletNotFoundException extends DexException {
	private static final long serialVersionUID = 3989044990128336403L;

    /**
     * Instantiates a new Private wallet not found exception.
     *
     * @param uid    the uid
     * @param type   the type
     * @param symbol the symbol
     */
    public PrivateWalletNotFoundException(String uid, String type, String symbol) {
		super(DexExceptionTypeEnum.PRIVATE_WALLET_NOT_FOUND, uid, type, symbol);
	}

    /**
     * Instantiates a new Private wallet not found exception.
     *
     * @param cause  the cause
     * @param uid    the uid
     * @param type   the type
     * @param symbol the symbol
     */
    public PrivateWalletNotFoundException(Throwable cause, String uid, String type, String symbol) {
		super(cause, DexExceptionTypeEnum.PRIVATE_WALLET_NOT_FOUND, uid, type, symbol);
	}
}
