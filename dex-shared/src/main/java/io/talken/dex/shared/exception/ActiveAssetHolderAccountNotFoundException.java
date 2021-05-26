package io.talken.dex.shared.exception;

/**
 * The type Active asset holder account not found exception.
 */
public class ActiveAssetHolderAccountNotFoundException extends DexException {
	private static final long serialVersionUID = -80743841960571504L;

    /**
     * Instantiates a new Active asset holder account not found exception.
     *
     * @param type the type
     */
    public ActiveAssetHolderAccountNotFoundException(String type) {
		super(DexExceptionTypeEnum.ACTIVE_ASSET_HOLDER_NOT_FOUND, type);
	}
}
