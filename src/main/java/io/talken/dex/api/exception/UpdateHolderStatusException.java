package io.talken.dex.api.exception;

public class UpdateHolderStatusException extends DexException {
	private static final long serialVersionUID = 2661686297405914829L;

	public UpdateHolderStatusException(String type, String accountID, String message) {
		super(DexExceptionTypeEnum.CANNOT_UPDATE_HOLDER_STATUS, type, accountID, message);
	}
}