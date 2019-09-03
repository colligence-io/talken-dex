package io.talken.dex.shared.exception;

public class BlockChainPlatformNotSupportedException extends DexException {

	public BlockChainPlatformNotSupportedException(String symbol) {
		super(DexExceptionTypeEnum.BLOCKCHAIN_PLATFORM_NOT_SUPPORTED, symbol);
	}

	public BlockChainPlatformNotSupportedException(Throwable cause, String symbol) {
		super(cause, DexExceptionTypeEnum.BLOCKCHAIN_PLATFORM_NOT_SUPPORTED, symbol);
	}
}
