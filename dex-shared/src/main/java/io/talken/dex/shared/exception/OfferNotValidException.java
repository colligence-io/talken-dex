package io.talken.dex.shared.exception;

/**
 * The type Offer not valid exception.
 */
public class OfferNotValidException extends DexException {
	private static final long serialVersionUID = 5484910683302552079L;

    /**
     * Instantiates a new Offer not valid exception.
     *
     * @param offerId the offer id
     * @param message the message
     */
    public OfferNotValidException(long offerId, String message) {
		super(DexExceptionTypeEnum.OFFER_NOT_VALID, offerId, message);
	}
}
