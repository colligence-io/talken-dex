package io.colligence.talken.dex.config.auth;

public class AccessTokenValidationException extends AuthenticationException {
	private static final long serialVersionUID = -8194226880434574857L;

	protected AccessTokenValidationException(String s) {
		super(s);
	}

	protected AccessTokenValidationException(String s, Throwable throwable) {
		super(s, throwable);
	}
}
