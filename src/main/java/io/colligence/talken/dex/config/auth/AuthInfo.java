package io.colligence.talken.dex.config.auth;

import io.colligence.talken.common.persistence.jooq.tables.pojos.User;
import io.colligence.talken.dex.exception.UnauthorizedException;

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