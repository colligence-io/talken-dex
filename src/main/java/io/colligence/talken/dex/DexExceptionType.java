package io.colligence.talken.dex;

import io.colligence.talken.common.CLGException;

public enum DexExceptionType implements CLGException.ExceptionType {
	AUTH_REQUIRED(0),
	AUTH_PROVIDER_NOT_ACCEPTED(1),
	USER_TERMS_NOT_AGREED(2),
	USER_PRIVACY_NOT_AGREED(3),
	GENERATE_TOKEN_ERROR(4),
	BANNED_USER_RESTRICTION(5),
	APPROVED_USER_RESTRICTION(6),
	ADMIN_USER_RESTRICTION(7),
	JWT_TOKEN_VALIDATE_ERROR(8),
	GENERAL_DATABASE_INVOKED_ERROR(9),
	DUPLICATED_POST_REPORT_ERROR(10),
	FREEZED_POST(11),
	FREEZED_REPLY(12),
	NOT_AUTHENTICATED_SMS(13),
	NOT_EXIST_SMS(14),
	NOT_EXIST_SMS_MESSAGE(15),
	USER_NOT_FOUND(16);

	private final int eCode;
	private final String messageKey;

	DexExceptionType(int eCode) {
		this.eCode = CLGException.buildErrorCode(CLGException.Module.API, eCode);
		this.messageKey = CLGException.buildMessageKey(CLGException.Module.API.toString(), this.toString());
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
