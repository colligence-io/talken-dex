package io.talken.dex.shared.exception;

/**
 * The type Trade wallet rebalance exception.
 */
public class TradeWalletRebalanceException extends DexException {
	private static final long serialVersionUID = 3707821456533465408L;

    /**
     * Instantiates a new Trade wallet rebalance exception.
     *
     * @param message the message
     */
    public TradeWalletRebalanceException(String message) {
		super(DexExceptionTypeEnum.TRADE_WALLET_REBALANCE_FAILED, message);
	}

    /**
     * Instantiates a new Trade wallet rebalance exception.
     *
     * @param cause   the cause
     * @param message the message
     */
    public TradeWalletRebalanceException(Throwable cause, String message) {
		super(cause, DexExceptionTypeEnum.TRADE_WALLET_REBALANCE_FAILED, message);
	}
}
