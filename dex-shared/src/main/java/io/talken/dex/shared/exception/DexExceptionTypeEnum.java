package io.talken.dex.shared.exception;

import io.talken.common.exception.TalkenException;

public enum DexExceptionTypeEnum implements TalkenException.ExceptionTypeEnum {
	INTERNAL_SERVER_ERROR(0),
	UNAUTHORIZED(1),
	PARAMETER_VIOLATION(2),
	TASK_NOT_FOUND(3),
	STELLAR_EXCEPTION(4),
	TASK_INTEGRITY_CHECK_FAILED(6),
	ACCOUNT_NOT_FOUND(7),
	TX_HASH_NOT_MATCH(8),
	TRANSACTION_RESULT_PROCESSING_ERROR(9),
	SIGNATURE_VERIFICATION_FAILED(10),
	ASSET_CONVERT_ERROR(11),
	BALANCE_NOT_ENOUGH(12),
	BCTX_EXCEPTION(13),
	EFFECTIVE_AMOUNT_NEGATIVE(14),
	SWAP_SERVICE_NOT_AVAILABLE(15),
	SWAP_UNDER_MINIMUM_AMOUNT(16),
	SWAP_PATH_NOT_AVAILABLE(17),
	SWAP_PREDICTION_THRESHOLD(18),
	TRADE_WALLET_CREATE_FAILED(19),
	TRADE_WALLET_REBALANCE_FAILED(20),
	DUPLICATED_TASK_FOUND(21),
	OFFER_NOT_VALID(22),

	// FROM MAS
	CANNOT_UPDATE_HOLDER_STATUS(51),
	ACTIVE_ASSET_HOLDER_NOT_FOUND(52),
	SIGNING_ERROR(53),
	CANNOT_LOAD_TOKEN_META_DATA(54),
	BLOCKCHAIN_PLATFORM_NOT_SUPPORTED(55);

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
