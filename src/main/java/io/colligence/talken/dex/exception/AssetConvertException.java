package io.colligence.talken.dex.exception;

public class AssetConvertException extends DexException {
	private static final long serialVersionUID = 1292882661154408364L;
	private String base;
	private String counter;

	public AssetConvertException(String base, String counter) {
		super(DexExceptionTypeEnum.ASSET_CONVERT_ERROR, base, counter);
		this.base = base;
		this.counter = counter;
	}

	public AssetConvertException(Throwable cause, String base, String counter) {
		super(cause, DexExceptionTypeEnum.ASSET_CONVERT_ERROR, base, counter);
		this.base = base;
		this.counter = counter;
	}

	public String getBase() {
		return base;
	}

	public String getCounter() {
		return counter;
	}
}
