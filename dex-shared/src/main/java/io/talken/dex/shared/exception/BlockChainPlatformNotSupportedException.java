package io.talken.dex.shared.exception;

/**
 * The type Block chain platform not supported exception.
 */
public class BlockChainPlatformNotSupportedException extends DexException {

    /**
     * Instantiates a new Block chain platform not supported exception.
     *
     * @param symbol the symbol
     */
    public BlockChainPlatformNotSupportedException(String symbol) {
		super(DexExceptionTypeEnum.BLOCKCHAIN_PLATFORM_NOT_SUPPORTED, symbol);
	}

    /**
     * Instantiates a new Block chain platform not supported exception.
     *
     * @param cause  the cause
     * @param symbol the symbol
     */
    public BlockChainPlatformNotSupportedException(Throwable cause, String symbol) {
		super(cause, DexExceptionTypeEnum.BLOCKCHAIN_PLATFORM_NOT_SUPPORTED, symbol);
	}
}
