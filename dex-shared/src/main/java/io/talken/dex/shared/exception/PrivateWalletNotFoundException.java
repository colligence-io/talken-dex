package io.talken.dex.shared.exception;

public class PrivateWalletNotFoundException extends DexException {
	private static final long serialVersionUID = 3989044990128336403L;

	public PrivateWalletNotFoundException(String uid, String type, String symbol) {
		super(DexExceptionTypeEnum.PRIVATE_WALLET_NOT_FOUND, uid, type, symbol);
	}

	public PrivateWalletNotFoundException(Throwable cause, String uid, String type, String symbol) {
		super(cause, DexExceptionTypeEnum.PRIVATE_WALLET_NOT_FOUND, uid, type, symbol);
	}
}
