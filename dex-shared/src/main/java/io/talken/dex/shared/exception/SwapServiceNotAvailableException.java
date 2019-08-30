package io.talken.dex.shared.exception;

public class SwapServiceNotAvailableException extends DexException {

	public SwapServiceNotAvailableException(String sourceAssetCode) {
		super(DexExceptionTypeEnum.SWAP_SERVICE_NOT_AVAILABLE, sourceAssetCode);
	}

	public SwapServiceNotAvailableException(Throwable cause, String sourceAssetCode) {
		super(cause, DexExceptionTypeEnum.SWAP_SERVICE_NOT_AVAILABLE, sourceAssetCode);
	}
}
