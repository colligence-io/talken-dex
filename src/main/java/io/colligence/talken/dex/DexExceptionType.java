package io.colligence.talken.dex;

import io.colligence.talken.common.CLGException;

public enum DexExceptionType implements CLGException.ExceptionType {
	ACCOUNT_NOT_FOUND(0),
	ASSET_TYPE_NOT_FOUND(1),
	TX_TYPE_NOT_SUPPORTED(2),
	PARAMETER_REQUIRED(3);

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
