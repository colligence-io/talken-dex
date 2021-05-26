package io.talken.dex.shared.service.integration.signer;

import io.talken.common.util.UTCUtil;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * The type Sign server access token.
 */
@Data
public class SignServerAccessToken {
	// token update before expires, at maximum
	private static final int TOKEN_UPDATE_BEFORE_EXPIRES_MAX = 120;

	private String token = null;
	private long tokenExpires = 0;
	@Setter(AccessLevel.NONE)
	private long updateBefore = 0;
	private Map<String, String> answers = new HashMap<>();

    /**
     * Is valid boolean.
     *
     * @return the boolean
     */
    public boolean isValid() {
		return token != null;
	}

    /**
     * Sets token expires.
     *
     * @param tokenExpires the token expires
     */
    public void setTokenExpires(long tokenExpires) {
		this.tokenExpires = tokenExpires;
		this.updateBefore = Math.min((tokenExpires - UTCUtil.getNowTimestamp_s()) * 80 / 100, TOKEN_UPDATE_BEFORE_EXPIRES_MAX);
	}

    /**
     * Is expired boolean.
     *
     * @return the boolean
     */
    public boolean isExpired() {
		return tokenExpires == 0 || getRemainedTTL() <= 0;
	}

    /**
     * check if token need to be updated
     *
     * @return boolean
     */
    public boolean needsUpdate() {
		return tokenExpires == 0 || getRemainedTBU() <= 0;
	}

    /**
     * token time to live
     *
     * @return remained ttl
     */
    public long getRemainedTTL() {
		return tokenExpires - UTCUtil.getNowTimestamp_s();
	}

    /**
     * time before update
     *
     * @return remained tbu
     */
    public long getRemainedTBU() {
		return tokenExpires - UTCUtil.getNowTimestamp_s() - updateBefore;
	}
}
