package io.talken.dex.shared.exception;

/**
 * The type Token meta load exception.
 */
public class TokenMetaLoadException extends DexException {
	private static final long serialVersionUID = 5593557515932630983L;

    /**
     * Instantiates a new Token meta load exception.
     *
     * @param message the message
     */
    public TokenMetaLoadException(String message) {
		super(DexExceptionTypeEnum.CANNOT_LOAD_TOKEN_META_DATA, message);
	}

    /**
     * Instantiates a new Token meta load exception.
     *
     * @param cause the cause
     */
    public TokenMetaLoadException(Throwable cause) {
		super(cause, DexExceptionTypeEnum.CANNOT_LOAD_TOKEN_META_DATA, cause.getMessage());
	}

    /**
     * Instantiates a new Token meta load exception.
     *
     * @param cause   the cause
     * @param message the message
     */
    public TokenMetaLoadException(Throwable cause, String message) {
		super(cause, DexExceptionTypeEnum.CANNOT_LOAD_TOKEN_META_DATA, message);
	}
}
