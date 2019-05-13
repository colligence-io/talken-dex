package io.talken.dex.shared.exception;

import io.talken.common.exception.TalkenException;

public enum DexExceptionTypeEnum implements TalkenException.ExceptionTypeEnum {
	INTERNAL_SERVER_ERROR(0),
	UNAUTHORIZED(1),
	PARAMETER_VIOLATION(2),
	TASK_NOT_FOUND(3),
	STELLAR_EXCEPTION(4),
	API_RETURNED_ERROR(5),
	TASK_INTEGRITY_CHECK_FAILED(6),
	ACCOUNT_NOT_FOUND(7),
	TX_HASH_NOT_MATCH(8),
	TRANSACTION_RESULT_PROCESSING_ERROR(9),
	SIGNATURE_VERIFICATION_FAILED(10),
	ASSET_CONVERT_ERROR(11),
	BALANCE_NOT_ENOUGH(12),
	BCTX_EXCEPTION(13),
	EFFECTIVE_AMOUNT_NEGATIVE(14),
	// FROM MAS
	CANNOT_UPDATE_HOLDER_STATUS(51),
	ACTIVE_ASSET_HOLDER_NOT_FOUND(52),
	SIGNING_ERROR(53),
	CANNOT_LOAD_TOKEN_META_DATA(54);

	private final int code;

	DexExceptionTypeEnum(int code) {
		this.code = code;
	}


	@Override
	public TalkenException.Module getModule() {
		return TalkenException.Module.DEX;
	}

	@Override
	public int getCode() {
		return code;
	}

	@Override
	public String getName() {
		return name();
	}
}
