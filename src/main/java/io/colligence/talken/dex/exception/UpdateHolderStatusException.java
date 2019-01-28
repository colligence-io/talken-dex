package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class UpdateHolderStatusException extends DexException {
	private static final long serialVersionUID = 2661686297405914829L;

	public UpdateHolderStatusException(String type, String accountID, String message) {
		super(DexExceptionType.CANNOT_UPDATE_HOLDER_STATUS, type, accountID, message);
	}
}
