package io.talken.dex.shared.exception;

public class StakingEventNotFoundException extends DexException {
	private static final long serialVersionUID = -163911762562614712L;

    public StakingEventNotFoundException(long stakingId) {
        super(DexExceptionTypeEnum.STAKING_EVENT_NOT_FOUND, "(id : " + stakingId + ")");
    }

	public StakingEventNotFoundException(String stakingCode, String symbol) {
		super(DexExceptionTypeEnum.STAKING_EVENT_NOT_FOUND, "(code : " + stakingCode + "/" + symbol + ")");
	}

	public StakingEventNotFoundException(Throwable cause, String stakingCode, String symbol) {
		super(cause, DexExceptionTypeEnum.STAKING_EVENT_NOT_FOUND, "(code : " + stakingCode + "/" + symbol + ")");
	}
}
