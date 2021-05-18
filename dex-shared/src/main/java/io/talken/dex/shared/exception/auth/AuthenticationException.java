package io.talken.dex.shared.exception.auth;

/**
 * The type Authentication exception.
 */
public class AuthenticationException extends RuntimeException {
	private static final long serialVersionUID = -6905732241065879487L;

    /**
     * Instantiates a new Authentication exception.
     */
    public AuthenticationException() {
		this("Authentication Failed");
	}

    /**
     * Instantiates a new Authentication exception.
     *
     * @param s the s
     */
    public AuthenticationException(String s) {
		super(s);
	}

    /**
     * Instantiates a new Authentication exception.
     *
     * @param s         the s
     * @param throwable the throwable
     */
    public AuthenticationException(String s, Throwable throwable) {
		super(s, throwable);
	}

    /**
     * Instantiates a new Authentication exception.
     *
     * @param throwable the throwable
     */
    public AuthenticationException(Throwable throwable) {
		super(throwable);
	}
}
