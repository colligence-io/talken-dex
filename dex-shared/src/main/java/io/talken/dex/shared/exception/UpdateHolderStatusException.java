package io.talken.dex.shared.exception;

/**
 * The type Update holder status exception.
 */
public class UpdateHolderStatusException extends DexException {
	private static final long serialVersionUID = 2661686297405914829L;

    /**
     * Instantiates a new Update holder status exception.
     *
     * @param type      the type
     * @param accountID the account id
     * @param message   the message
     */
    public UpdateHolderStatusException(String type, String accountID, String message) {
		super(DexExceptionTypeEnum.CANNOT_UPDATE_HOLDER_STATUS, type, accountID, message);
	}
}
