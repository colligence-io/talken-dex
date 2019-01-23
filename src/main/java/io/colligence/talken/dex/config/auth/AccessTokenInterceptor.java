package io.colligence.talken.dex.config.auth;

import io.colligence.talken.common.util.JwtUtil;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexSettings;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.function.Consumer;

public class AccessTokenInterceptor implements HandlerInterceptor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AccessTokenInterceptor.class);

	@Autowired
	private DexSettings dexSettings;

	// request scoped bean
	@Autowired
	private AuthInfo authInfo;

	private JwtUtil jwtUtil;

	private static String tokenHeader;

	private static Consumer<AuthInfo> AuthSkipper = null;

	public static void setAuthSkipper(Long fixedUserId) {
		AuthSkipper = authInfo -> authInfo.setUserId(fixedUserId);
	}

	@PostConstruct
	private void init() {
		tokenHeader = dexSettings.getAccessToken().getTokenHeader();
		jwtUtil = new JwtUtil(dexSettings.getAccessToken().getJwtSecret(), dexSettings.getAccessToken().getJwtExpiration());
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if(AuthSkipper != null) {
			AuthSkipper.accept(authInfo);
			return true;
		}

		try {
			String token = request.getHeader(tokenHeader);
			if(token == null || token.isEmpty()) {
				throw new AccessTokenNotFoundException();
			} else {
				try {
					Long userId = jwtUtil.getUserIdFromJWT(token);

					// TODO : check userId is valid from DB

					authInfo.setUserId(userId);
				} catch(SignatureException ex) {
					// Invalid JWT signature
					throw new AccessTokenValidationException("Invalid AccessToken", ex);
				} catch(MalformedJwtException ex) {
					// Invalid JWT token
					throw new AccessTokenValidationException("Invalid AccessToken", ex);
				} catch(ExpiredJwtException ex) {
					// Expired JWT token
					throw new AccessTokenValidationException("Invalid AccessToken", ex);
				} catch(UnsupportedJwtException ex) {
					// Unsupported JWT token
					throw new AccessTokenValidationException("Invalid AccessToken", ex);
				} catch(IllegalArgumentException ex) {
					// JWT claims string is empty.
					throw new AccessTokenValidationException("Invalid AccessToken", ex);
				}
			}
		} catch(AuthenticationException ex) {
			authInfo.setAuthException(ex);
		}
		return true;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

	}
}
