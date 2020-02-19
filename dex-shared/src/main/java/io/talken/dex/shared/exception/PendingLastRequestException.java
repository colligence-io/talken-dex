package io.talken.dex.shared.exception;

public class PendingLastRequestException extends DexException {
	private static final long serialVersionUID = -7846737325916872022L;

	public PendingLastRequestException() {
		super(DexExceptionTypeEnum.PENDING_LAST_REQUEST);
	}

	public PendingLastRequestException(Throwable cause) {
		super(cause, DexExceptionTypeEnum.PENDING_LAST_REQUEST);
	}
}
