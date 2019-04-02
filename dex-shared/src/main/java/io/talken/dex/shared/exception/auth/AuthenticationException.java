package io.talken.dex.shared.exception.auth;

public class AuthenticationException extends RuntimeException {
	private static final long serialVersionUID = -6905732241065879487L;

	public AuthenticationException() {
		this("Authentication Failed");
	}

	public AuthenticationException(String s) {
		super(s);
	}

	public AuthenticationException(String s, Throwable throwable) {
		super(s, throwable);
	}

	public AuthenticationException(Throwable throwable) {
		super(throwable);
	}
}
