package io.talken.dex.governance.service.bctx.monitor.stellar;

/**
 * The type Dex task transaction process result.
 */
public class DexTaskTransactionProcessResult {

	private boolean isSuccess;
	private DexTaskTransactionProcessError error;

    /**
     * Success dex task transaction process result.
     *
     * @return the dex task transaction process result
     */
    public static DexTaskTransactionProcessResult success() {
		return new DexTaskTransactionProcessResult(true);
	}

    /**
     * Error dex task transaction process result.
     *
     * @param error the error
     * @return the dex task transaction process result
     */
    public static DexTaskTransactionProcessResult error(DexTaskTransactionProcessError error) {
		return new DexTaskTransactionProcessResult(false, error);
	}

    /**
     * Error dex task transaction process result.
     *
     * @param code      the code
     * @param exception the exception
     * @return the dex task transaction process result
     */
    public static DexTaskTransactionProcessResult error(String code, Throwable exception) {
		return new DexTaskTransactionProcessResult(false, new DexTaskTransactionProcessError(code, exception, exception.getClass().getSimpleName() + " : " + exception.getMessage()));
	}

	private DexTaskTransactionProcessResult(boolean isSuccess) {
		this.isSuccess = isSuccess;
		this.error = null;
	}

	private DexTaskTransactionProcessResult(boolean isSuccess, DexTaskTransactionProcessError error) {
		this.isSuccess = isSuccess;
		this.error = error;
	}

    /**
     * Is success boolean.
     *
     * @return the boolean
     */
    public boolean isSuccess() {
		return isSuccess;
	}

    /**
     * Gets error.
     *
     * @return the error
     */
    public DexTaskTransactionProcessError getError() {
		return error;
	}
}
