package io.talken.dex.shared.exception;

public class TradeWalletCreateFailedException extends DexException {
	private static final long serialVersionUID = -7573487994665360089L;

	public TradeWalletCreateFailedException(String message) {
		super(DexExceptionTypeEnum.TRADE_WALLET_CREATE_FAILED, message);
	}

	public TradeWalletCreateFailedException(Throwable cause, String message) {
		super(cause, DexExceptionTypeEnum.TRADE_WALLET_CREATE_FAILED, message);
	}
}
