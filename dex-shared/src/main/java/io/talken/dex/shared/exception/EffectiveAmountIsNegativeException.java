package io.talken.dex.shared.exception;

public class EffectiveAmountIsNegativeException extends DexException {
	private static final long serialVersionUID = 2205883571063344125L;

	public EffectiveAmountIsNegativeException(String type, String required) {
		super(DexExceptionTypeEnum.EFFECTIVE_AMOUNT_NEGATIVE, type, required);
	}

	public EffectiveAmountIsNegativeException(Throwable cause, String type, String required) {
		super(cause, DexExceptionTypeEnum.EFFECTIVE_AMOUNT_NEGATIVE, type, required);
	}
}
