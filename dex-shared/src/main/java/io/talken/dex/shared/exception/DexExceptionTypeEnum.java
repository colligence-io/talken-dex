package io.talken.dex.shared.exception;

import io.talken.common.exception.TalkenException;

/**
 * The enum Dex exception type enum.
 */
public enum DexExceptionTypeEnum implements TalkenException.ExceptionTypeEnum {
    /**
     * Internal server error dex exception type enum.
     */
    INTERNAL_SERVER_ERROR(0),
    /**
     * Unauthorized dex exception type enum.
     */
    UNAUTHORIZED(1),
    /**
     * Parameter violation dex exception type enum.
     */
    PARAMETER_VIOLATION(2),
    /**
     * Task not found dex exception type enum.
     */
    TASK_NOT_FOUND(3),
    /**
     * Stellar exception dex exception type enum.
     */
    STELLAR_EXCEPTION(4),
    /**
     * Task integrity check failed dex exception type enum.
     */
    TASK_INTEGRITY_CHECK_FAILED(6),
    /**
     * Account not found dex exception type enum.
     */
    ACCOUNT_NOT_FOUND(7),
    /**
     * Tx hash not match dex exception type enum.
     */
    TX_HASH_NOT_MATCH(8),
    /**
     * Transaction result processing error dex exception type enum.
     */
    TRANSACTION_RESULT_PROCESSING_ERROR(9),
    /**
     * Signature verification failed dex exception type enum.
     */
    SIGNATURE_VERIFICATION_FAILED(10),
    /**
     * Asset convert error dex exception type enum.
     */
    ASSET_CONVERT_ERROR(11),
    /**
     * Balance not enough dex exception type enum.
     */
    BALANCE_NOT_ENOUGH(12),
    /**
     * Bctx exception dex exception type enum.
     */
    BCTX_EXCEPTION(13),
    /**
     * Effective amount negative dex exception type enum.
     */
    EFFECTIVE_AMOUNT_NEGATIVE(14),
    /**
     * Trade wallet create failed dex exception type enum.
     */
    TRADE_WALLET_CREATE_FAILED(19),
    /**
     * Trade wallet rebalance failed dex exception type enum.
     */
    TRADE_WALLET_REBALANCE_FAILED(20),
    /**
     * Duplicated task found dex exception type enum.
     */
    DUPLICATED_TASK_FOUND(21),
    /**
     * Offer not valid dex exception type enum.
     */
    OFFER_NOT_VALID(22),
    /**
     * Ownership mismatch dex exception type enum.
     */
    OWNERSHIP_MISMATCH(23),
    /**
     * Private wallet not found dex exception type enum.
     */
    PRIVATE_WALLET_NOT_FOUND(24),
    /**
     * Pending last request dex exception type enum.
     */
    PENDING_LAST_REQUEST(25),

    /**
     * The Cannot update holder status.
     */
// FROM MAS
	CANNOT_UPDATE_HOLDER_STATUS(51),
    /**
     * Active asset holder not found dex exception type enum.
     */
    ACTIVE_ASSET_HOLDER_NOT_FOUND(52),
    /**
     * Signing error dex exception type enum.
     */
    SIGNING_ERROR(53),
    /**
     * Cannot load token meta data dex exception type enum.
     */
    CANNOT_LOAD_TOKEN_META_DATA(54),
    /**
     * Blockchain platform not supported dex exception type enum.
     */
    BLOCKCHAIN_PLATFORM_NOT_SUPPORTED(55),

    /**
     * Staking event not found dex exception type enum.
     */
// STAKING
    STAKING_EVENT_NOT_FOUND(100),
    /**
     * Staking amount enough dex exception type enum.
     */
    STAKING_AMOUNT_ENOUGH(101),
    /**
     * Staking user enough dex exception type enum.
     */
    STAKING_USER_ENOUGH(102),
    /**
     * Staking already exists dex exception type enum.
     */
    STAKING_ALREADY_EXISTS(103),
    /**
     * Unstaking after staking dex exception type enum.
     */
    UNSTAKING_AFTER_STAKING(104),
    /**
     * Unstaking before expire dex exception type enum.
     */
    UNSTAKING_BEFORE_EXPIRE(105),
    /**
     * Staking before start dex exception type enum.
     */
    STAKING_BEFORE_START(106),
    /**
     * Staking after end dex exception type enum.
     */
    STAKING_AFTER_END(107),
    /**
     * Staking balance not enough dex exception type enum.
     */
    STAKING_BALANCE_NOT_ENOUGH(108),
    /**
     * Staking too little amount dex exception type enum.
     */
    STAKING_TOO_LITTLE_AMOUNT(109),
    /**
     * Staking too much amount dex exception type enum.
     */
    STAKING_TOO_MUCH_AMOUNT(110),
    /**
     * Staking too over amount dex exception type enum.
     */
    STAKING_TOO_OVER_AMOUNT(111),
    /**
     * Unstaking disabled dex exception type enum.
     */
    UNSTAKING_DISABLED(112),
    /**
     * Unstaking too over amount dex exception type enum.
     */
    UNSTAKING_TOO_OVER_AMOUNT(113);

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
