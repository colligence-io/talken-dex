package io.talken.dex.api.config.auth;

import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.dex.shared.exception.UnauthorizedException;
import io.talken.dex.shared.exception.auth.AuthenticationException;

/**
 * The type Auth info.
 */
public class AuthInfo {
	private User user = null;
	private UnauthorizedException uae = null;

    /**
     * set user
     *
     * @param user the user
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

    /**
     * Is logged in boolean.
     *
     * @return the boolean
     */
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
     * @param aex the aex
     */
    void setAuthException(AuthenticationException aex) {
		this.user = null;
		this.uae = new UnauthorizedException(aex);
	}

    /**
     * check auth has failed
     *
     * @throws UnauthorizedException the unauthorized exception
     */
    public void checkAuth() throws UnauthorizedException {
		if(this.uae != null) throw uae;
	}
}