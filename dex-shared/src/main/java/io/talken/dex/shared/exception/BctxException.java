package io.talken.dex.shared.exception;

public class BctxException extends DexException {
	private static final long serialVersionUID = 6161809053200680035L;

	public BctxException(String code, String message) {
		super(DexExceptionTypeEnum.BCTX_EXCEPTION, code, message);
	}

	public BctxException(Throwable cause, String code, String message) {
		super(cause, DexExceptionTypeEnum.BCTX_EXCEPTION, code, message);
	}
}
