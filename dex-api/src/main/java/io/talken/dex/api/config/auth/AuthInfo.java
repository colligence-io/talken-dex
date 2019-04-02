package io.talken.dex.api.config.auth;

import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.dex.shared.exception.UnauthorizedException;
import io.talken.dex.shared.exception.auth.AuthenticationException;

public class AuthInfo {
	private User user = null;
	private UnauthorizedException uae = null;

	public void setUser(User user) {
		this.user = user;
		this.uae = null;
	}

	public User getUser() {
		return this.user;
	}

	public Long getUserId() {
		return this.user.getId();
	}

	public void setAuthException(AuthenticationException aex) {
		this.user = null;
		this.uae = new UnauthorizedException(aex);
	}

	public void checkAuth() throws UnauthorizedException {
		if(this.uae != null) throw uae;
	}
}