package io.talken.dex.shared.exception;

/**
 * The type Asset convert exception.
 */
public class AssetConvertException extends DexException {
	private static final long serialVersionUID = 1292882661154408364L;
	private String base;
	private String counter;

    /**
     * Instantiates a new Asset convert exception.
     *
     * @param base    the base
     * @param counter the counter
     */
    public AssetConvertException(String base, String counter) {
		super(DexExceptionTypeEnum.ASSET_CONVERT_ERROR, base, counter);
		this.base = base;
		this.counter = counter;
	}

    /**
     * Instantiates a new Asset convert exception.
     *
     * @param cause   the cause
     * @param base    the base
     * @param counter the counter
     */
    public AssetConvertException(Throwable cause, String base, String counter) {
		super(cause, DexExceptionTypeEnum.ASSET_CONVERT_ERROR, base, counter);
		this.base = base;
		this.counter = counter;
	}

    /**
     * Gets base.
     *
     * @return the base
     */
    public String getBase() {
		return base;
	}

    /**
     * Gets counter.
     *
     * @return the counter
     */
    public String getCounter() {
		return counter;
	}
}
