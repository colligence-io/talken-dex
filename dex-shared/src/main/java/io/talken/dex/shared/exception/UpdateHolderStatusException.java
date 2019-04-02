package io.talken.dex.shared.exception;

public class UpdateHolderStatusException extends DexException {
	private static final long serialVersionUID = 2661686297405914829L;

	public UpdateHolderStatusException(String type, String accountID, String message) {
		super(DexExceptionTypeEnum.CANNOT_UPDATE_HOLDER_STATUS, type, accountID, message);
	}
}
