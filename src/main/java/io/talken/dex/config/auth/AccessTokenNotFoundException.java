package io.talken.dex.config.auth;

public class AccessTokenNotFoundException extends AuthenticationException {
	private static final long serialVersionUID = 3788255657998174772L;

	public AccessTokenNotFoundException() {
		super("AccessToken not found");
	}
}
