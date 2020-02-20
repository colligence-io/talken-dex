package io.talken.dex.api.config.auth;

import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.dex.shared.exception.UnauthorizedException;
import io.talken.dex.shared.exception.auth.AuthenticationException;

public class AuthInfo {
	private User user = null;
	private UnauthorizedException uae = null;

	/**
	 * set user
	 *
	 * @param user
	 */
	void setUser(User user) {
		this.user = user;
		this.uae = null;
	}

	/**
	 * get user
	 *
	 * @return null if not logged
	 */
	public User getUser() {
		return this.user;
	}

	public boolean isLoggedIn() {
		return this.user != null;
	}

	/**
	 * get user Id
	 *
	 * @return user ID (Long), null if not logged
	 */
	public Long getUserId() {
		return isLoggedIn() ? this.user.getId() : null;
	}

	/**
	 * set auth exception if occured
	 *
	 * @param aex
	 */
	void setAuthException(AuthenticationException aex) {
		this.user = null;
		this.uae = new UnauthorizedException(aex);
	}

	/**
	 * check auth has failed
	 *
	 * @throws UnauthorizedException
	 */
	public void checkAuth() throws UnauthorizedException {
		if(this.uae != null) throw uae;
	}
}