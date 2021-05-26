package io.talken.dex.governance.service.bctx.monitor.stellar;

import org.slf4j.helpers.MessageFormatter;

/**
 * The type Dex task transaction process error.
 */
public class DexTaskTransactionProcessError extends RuntimeException {
	private static final long serialVersionUID = -7083397512485380153L;

	private String code;

    /**
     * Instantiates a new Dex task transaction process error.
     *
     * @param code the code
     */
    public DexTaskTransactionProcessError(String code) {
		super(code);
		this.code = code;
	}

    /**
     * Instantiates a new Dex task transaction process error.
     *
     * @param code    the code
     * @param message the message
     */
    public DexTaskTransactionProcessError(String code, String message) {
		super(message);
		this.code = code;
	}

    /**
     * Instantiates a new Dex task transaction process error.
     *
     * @param code   the code
     * @param format the format
     * @param arg    the arg
     */
    public DexTaskTransactionProcessError(String code, String format, Object arg) {
		this(code, MessageFormatter.format(format, arg).getMessage());
	}

    /**
     * Instantiates a new Dex task transaction process error.
     *
     * @param code   the code
     * @param format the format
     * @param arg1   the arg 1
     * @param arg2   the arg 2
     */
    public DexTaskTransactionProcessError(String code, String format, Object arg1, Object arg2) {
		this(code, MessageFormatter.format(format, arg1, arg2).getMessage());
	}

    /**
     * Instantiates a new Dex task transaction process error.
     *
     * @param code   the code
     * @param format the format
     * @param args   the args
     */
    public DexTaskTransactionProcessError(String code, String format, Object... args) {
		this(code, MessageFormatter.arrayFormat(format, args).getMessage());
	}

    /**
     * Instantiates a new Dex task transaction process error.
     *
     * @param code the code
     * @param ex   the ex
     */
    public DexTaskTransactionProcessError(String code, Throwable ex) {
		super(ex.getClass().getSimpleName() + " : " + ex.getMessage(), ex);
		this.code = code;
	}

    /**
     * Instantiates a new Dex task transaction process error.
     *
     * @param code    the code
     * @param ex      the ex
     * @param message the message
     */
    public DexTaskTransactionProcessError(String code, Throwable ex, String message) {
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
