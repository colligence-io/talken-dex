package io.talken.dex.shared.service.blockchain.stellar;

import org.slf4j.helpers.MessageFormatter;

/**
 * The type Stellar tx result parsing error.
 */
public class StellarTxResultParsingError extends RuntimeException {
	private static final long serialVersionUID = -7083397512485380153L;

	private String code;

    /**
     * Instantiates a new Stellar tx result parsing error.
     *
     * @param code the code
     */
    public StellarTxResultParsingError(String code) {
		super(code);
		this.code = code;
	}

    /**
     * Instantiates a new Stellar tx result parsing error.
     *
     * @param code    the code
     * @param message the message
     */
    public StellarTxResultParsingError(String code, String message) {
		super(message);
		this.code = code;
	}

    /**
     * Instantiates a new Stellar tx result parsing error.
     *
     * @param code   the code
     * @param format the format
     * @param arg    the arg
     */
    public StellarTxResultParsingError(String code, String format, Object arg) {
		this(code, MessageFormatter.format(format, arg).getMessage());
	}

    /**
     * Instantiates a new Stellar tx result parsing error.
     *
     * @param code   the code
     * @param format the format
     * @param arg1   the arg 1
     * @param arg2   the arg 2
     */
    public StellarTxResultParsingError(String code, String format, Object arg1, Object arg2) {
		this(code, MessageFormatter.format(format, arg1, arg2).getMessage());
	}

    /**
     * Instantiates a new Stellar tx result parsing error.
     *
     * @param code   the code
     * @param format the format
     * @param args   the args
     */
    public StellarTxResultParsingError(String code, String format, Object... args) {
		this(code, MessageFormatter.arrayFormat(format, args).getMessage());
	}

    /**
     * Instantiates a new Stellar tx result parsing error.
     *
     * @param code the code
     * @param ex   the ex
     */
    public StellarTxResultParsingError(String code, Throwable ex) {
		super(ex.getClass().getSimpleName() + " : " + ex.getMessage(), ex);
		this.code = code;
	}

    /**
     * Instantiates a new Stellar tx result parsing error.
     *
     * @param code    the code
     * @param ex      the ex
     * @param message the message
     */
    public StellarTxResultParsingError(String code, Throwable ex, String message) {
		super(message + " [" + ex.getClass().getSimpleName() + " : " + ex.getMessage() + "]", ex);
		this.code = code;
	}

    /**
     * Gets code.
     *
     * @return the code
     */
    public String getCode() {
		return code;
	}
}
