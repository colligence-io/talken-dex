package io.talken.dex.shared.exception.auth;

/**
 * The type Access token validation exception.
 */
public class AccessTokenValidationException extends AuthenticationException {
	private static final long serialVersionUID = -8194226880434574857L;

    /**
     * Instantiates a new Access token validation exception.
     *
     * @param s the s
     */
    public AccessTokenValidationException(String s) {
		super(s);
	}

    /**
     * Instantiates a new Access token validation exception.
     *
     * @param s         the s
     * @param throwable the throwable
     */
    public AccessTokenValidationException(String s, Throwable throwable) {
		super(s, throwable);
	}
}
