package io.colligence.talken.dex;

import io.colligence.talken.common.CLGException;

public enum DexExceptionType implements CLGException.ExceptionType {
	INTERNAL_SERVER_ERROR(0),
	UNAUTHORIZED(1),
	PARAMETER_VIOLATION(2),
	TASK_NOT_FOUND(3),
	APICALL_ERROR(4),
	API_RETURNED_ERROR(5),
	TASK_INTEGRITY_CHECK_FAILED(6),
	ACCOUNT_NOT_FOUND(7),
	ASSET_TYPE_NOT_FOUND(8),
	TX_HASH_NOT_MATCH(9);

	private final int eCode;
	private final String messageKey;

	DexExceptionType(int eCode) {
		this.eCode = CLGException.buildErrorCode(CLGException.Module.DEX, eCode);
		this.messageKey = CLGException.buildMessageKey(CLGException.Module.DEX.toString(), this.toString());
	}

	@Override
	public int getCode() {
		return eCode;
	}

	@Override
	public String getMessageKey() {
		return messageKey;
	}
}
