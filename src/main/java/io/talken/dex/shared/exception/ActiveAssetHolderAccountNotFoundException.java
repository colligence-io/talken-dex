package io.talken.dex.shared.exception;

public class ActiveAssetHolderAccountNotFoundException extends DexException {
	private static final long serialVersionUID = -80743841960571504L;

	public ActiveAssetHolderAccountNotFoundException(String type) {
		super(DexExceptionTypeEnum.ACTIVE_ASSET_HOLDER_NOT_FOUND, type);
	}
}
