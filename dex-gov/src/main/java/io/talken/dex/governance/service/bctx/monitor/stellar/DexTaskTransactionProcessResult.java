package io.talken.dex.governance.service.bctx.monitor.stellar;

public class DexTaskTransactionProcessResult {

	private boolean isSuccess;
	private DexTaskTransactionProcessError error;

	public static DexTaskTransactionProcessResult success() {
		return new DexTaskTransactionProcessResult(true);
	}

	public static DexTaskTransactionProcessResult error(DexTaskTransactionProcessError error) {
		return new DexTaskTransactionProcessResult(false, error);
	}

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

	public boolean isSuccess() {
		return isSuccess;
	}

	public DexTaskTransactionProcessError getError() {
		return error;
	}
}
