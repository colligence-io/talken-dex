package io.colligence.talken.dex.config.auth;

import io.colligence.talken.dex.exception.UnauthorizedException;

public class AuthInfo {
	private Long userId = null;
	private UnauthorizedException uae = null;

	public void setUserId(Long userId) {
		this.userId = userId;
		this.uae = null;
	}

	public void setAuthException(AuthenticationException aex) {
		this.userId = null;
		this.uae = new UnauthorizedException(aex);
	}

	public Long getUserId() throws UnauthorizedException {
		if(this.uae != null) throw uae;
		return userId;
	}
}
