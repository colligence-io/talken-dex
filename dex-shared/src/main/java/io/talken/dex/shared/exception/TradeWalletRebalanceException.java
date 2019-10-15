package io.talken.dex.shared.exception;

public class TradeWalletRebalanceException extends DexException {
	private static final long serialVersionUID = 3707821456533465408L;

	public TradeWalletRebalanceException(String message) {
		super(DexExceptionTypeEnum.TRADE_WALLET_REBALANCE_FAILED, message);
	}

	public TradeWalletRebalanceException(Throwable cause, String message) {
		super(cause, DexExceptionTypeEnum.TRADE_WALLET_REBALANCE_FAILED, message);
	}
}
