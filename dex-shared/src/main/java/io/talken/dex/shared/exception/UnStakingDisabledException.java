package io.talken.dex.shared.exception;

public class UnStakingDisabledException extends DexException {
	private static final long serialVersionUID = -960072183515804045L;

	public UnStakingDisabledException(String stakingCode, String symbol) {
		super(DexExceptionTypeEnum.UNSTAKING_DISABLED, stakingCode, symbol);
	}

	public UnStakingDisabledException(Throwable cause, String stakingCode, String symbol) {
		super(cause, DexExceptionTypeEnum.UNSTAKING_DISABLED, stakingCode, symbol);
	}
}
