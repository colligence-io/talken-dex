package io.colligence.talken.dex;

import io.colligence.talken.common.CLGException;

public enum DexExceptionType implements CLGException.ExceptionType {
	INTERNAL_SERVER_ERROR(0),
	PARAMETER_VIOLATION(1),
	TASK_NOT_FOUND(2),
	APICALL_ERROR(3),
	API_RETURNED_ERROR(4),
	TASK_INTEGRITY_CHECK_FAILED(5),
	ACCOUNT_NOT_FOUND(6),
	ASSET_TYPE_NOT_FOUND(7),
	TX_HASH_NOT_MATCH(8);

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
