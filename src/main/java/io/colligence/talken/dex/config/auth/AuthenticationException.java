package io.colligence.talken.dex.config.auth;

public class AuthenticationException extends RuntimeException {
	private static final long serialVersionUID = -6905732241065879487L;

	public AuthenticationException() {
		this("Authentication Failed");
	}

	protected AuthenticationException(String s) {
		super(s);
	}

	protected AuthenticationException(String s, Throwable throwable) {
		super(s, throwable);
	}

	protected AuthenticationException(Throwable throwable) {
		super(throwable);
	}
}
