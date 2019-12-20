package io.talken.dex.shared.exception;

public class OwnershipMismatchException extends DexException {
	private static final long serialVersionUID = -8398781281735864893L;

	public OwnershipMismatchException(String message) {
		super(DexExceptionTypeEnum.OWNERSHIP_MISMATCH, message);
	}
}
