package io.talken.dex.shared.exception;

public class OfferNotValidException extends DexException {
	private static final long serialVersionUID = 5484910683302552079L;

	public OfferNotValidException(long offerId, String message) {
		super(DexExceptionTypeEnum.OFFER_NOT_VALID, offerId, message);
	}
}
