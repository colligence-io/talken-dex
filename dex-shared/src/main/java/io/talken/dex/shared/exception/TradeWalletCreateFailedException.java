package io.talken.dex.shared.exception;

/**
 * The type Trade wallet create failed exception.
 */
public class TradeWalletCreateFailedException extends DexException {
	private static final long serialVersionUID = -7573487994665360089L;

    /**
     * Instantiates a new Trade wallet create failed exception.
     *
     * @param message the message
     */
    public TradeWalletCreateFailedException(String message) {
		super(DexExceptionTypeEnum.TRADE_WALLET_CREATE_FAILED, message);
	}

    /**
     * Instantiates a new Trade wallet create failed exception.
     *
     * @param cause   the cause
     * @param message the message
     */
    public TradeWalletCreateFailedException(Throwable cause, String message) {
		super(cause, DexExceptionTypeEnum.TRADE_WALLET_CREATE_FAILED, message);
	}
}
