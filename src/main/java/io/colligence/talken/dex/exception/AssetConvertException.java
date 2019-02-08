package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class AssetConvertException extends DexException {
	private static final long serialVersionUID = 1292882661154408364L;
	private String base;
	private String counter;

	public AssetConvertException(String base, String counter) {
		super(DexExceptionType.ASSET_CONVERT_ERROR, base, counter);
		this.base = base;
		this.counter = counter;
	}

	public AssetConvertException(Throwable cause, String base, String counter) {
		super(cause, DexExceptionType.ASSET_CONVERT_ERROR, base, counter);
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
