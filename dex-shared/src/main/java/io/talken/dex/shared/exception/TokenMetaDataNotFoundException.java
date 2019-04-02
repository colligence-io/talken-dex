package io.talken.dex.shared.exception;

public class TokenMetaDataNotFoundException extends DexException {
	private static final long serialVersionUID = -3337473019249235242L;

	String arg;

	public TokenMetaDataNotFoundException(String arg) {
		super(DexExceptionTypeEnum.TOKEN_META_DATA_NOT_FOUND, arg);
		this.arg = arg;
	}

	public TokenMetaDataNotFoundException(Throwable cause, String arg) {
		super(cause, DexExceptionTypeEnum.TOKEN_META_DATA_NOT_FOUND, arg);
		this.arg = arg;
	}

	public String getArg() {
		return arg;
	}
}
