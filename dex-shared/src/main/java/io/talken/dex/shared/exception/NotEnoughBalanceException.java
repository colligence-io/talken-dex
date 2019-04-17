package io.talken.dex.shared.exception;

public class NotEnoughBalanceException extends DexException {
	private static final long serialVersionUID = 4373066228855431143L;

	public NotEnoughBalanceException(String type, String required) {
		super(DexExceptionTypeEnum.BALANCE_NOT_ENOUGH, type, required);
	}

	public NotEnoughBalanceException(Throwable cause, String type, String required) {
		super(cause, DexExceptionTypeEnum.BALANCE_NOT_ENOUGH, type, required);
	}
}
