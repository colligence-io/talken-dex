package io.talken.dex.shared.exception.auth;

/**
 * The type Access token not found exception.
 */
public class AccessTokenNotFoundException extends AuthenticationException {
	private static final long serialVersionUID = 3788255657998174772L;

    /**
     * Instantiates a new Access token not found exception.
     */
    public AccessTokenNotFoundException() {
		super("AccessToken not found");
	}
}
