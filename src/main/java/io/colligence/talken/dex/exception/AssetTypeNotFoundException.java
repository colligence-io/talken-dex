package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class AssetTypeNotFoundException extends DexException {
	private static final long serialVersionUID = -3959223316740876234L;

	public AssetTypeNotFoundException(Object... args) {
		super(DexExceptionType.ASSET_TYPE_NOT_FOUND, args);
	}

	public AssetTypeNotFoundException(Throwable cause, Object... args) {
		super(cause, DexExceptionType.ASSET_TYPE_NOT_FOUND, args);
	}
}
