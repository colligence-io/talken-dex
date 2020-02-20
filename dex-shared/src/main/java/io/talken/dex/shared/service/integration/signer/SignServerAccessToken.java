package io.talken.dex.shared.service.integration.signer;

import io.talken.common.util.UTCUtil;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Data
public class SignServerAccessToken {
	// token update before expires, at maximum
	private static final int TOKEN_UPDATE_BEFORE_EXPIRES_MAX = 120;

	private String token = null;
	private long tokenExpires = 0;
	@Setter(AccessLevel.NONE)
	private long updateBefore = 0;
	private Map<String, String> answers = new HashMap<>();

	public boolean isValid() {
		return token != null;
	}

	public void setTokenExpires(long tokenExpires) {
		this.tokenExpires = tokenExpires;
		this.updateBefore = Math.min((tokenExpires - UTCUtil.getNowTimestamp_s()) * 80 / 100, TOKEN_UPDATE_BEFORE_EXPIRES_MAX);
	}

	public boolean isExpired() {
		return tokenExpires == 0 || getRemainedTTL() <= 0;
	}

	/**
	 * check if token need to be updated
	 *
	 * @return
	 */
	public boolean needsUpdate() {
		return tokenExpires == 0 || getRemainedTBU() <= 0;
	}

	/**
	 * token time to live
	 *
	 * @return
	 */
	public long getRemainedTTL() {
		return tokenExpires - UTCUtil.getNowTimestamp_s();
	}

	/**
	 * time before update
	 *
	 * @return
	 */
	public long getRemainedTBU() {
		return tokenExpires - UTCUtil.getNowTimestamp_s() - updateBefore;
	}
}
