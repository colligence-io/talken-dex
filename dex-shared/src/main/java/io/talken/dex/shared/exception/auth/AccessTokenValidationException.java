package io.talken.dex.shared.exception.auth;

public class AccessTokenValidationException extends AuthenticationException {
	private static final long serialVersionUID = -8194226880434574857L;

	public AccessTokenValidationException(String s) {
		super(s);
	}

	public AccessTokenValidationException(String s, Throwable throwable) {
		super(s, throwable);
	}
}
