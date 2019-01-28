package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;

public class ActiveAssetHolderAccountNotFoundException extends DexException {
	private static final long serialVersionUID = -80743841960571504L;

	public ActiveAssetHolderAccountNotFoundException(String type) {
		super(DexExceptionType.ACTIVE_ASSET_HOLDER_NOT_FOUND, type);
	}
}
