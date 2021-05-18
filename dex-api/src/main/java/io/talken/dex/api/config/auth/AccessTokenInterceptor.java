package io.talken.dex.api.config.auth;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.talken.common.exception.runtime.JwtValidateRuntimeException;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.service.JWTService;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.ApiSettings;
import io.talken.dex.shared.exception.auth.AccessTokenNotFoundException;
import io.talken.dex.shared.exception.auth.AccessTokenValidationException;
import io.talken.dex.shared.exception.auth.AuthenticationException;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static io.talken.common.persistence.jooq.Tables.USER;

/**
 * The type Access token interceptor.
 */
public class AccessTokenInterceptor implements HandlerInterceptor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AccessTokenInterceptor.class);

	@Autowired
	private ApiSettings apiSettings;

	@Autowired
	private DSLContext dslContext;

	// request scoped bean
	@Autowired
	private AuthInfo authInfo;

	@Autowired
	private JWTService jwtService;

	private static String tokenHeader;

	private static Long fixedUserId = null;

    /**
     * Sets auth skipper.
     *
     * @param userId the user id
     */
    public static void setAuthSkipper(Long userId) {
		fixedUserId = userId;
	}

	@PostConstruct
	private void init() {
		tokenHeader = apiSettings.getAccessToken().getTokenHeader();
//		jwtService = new JwtUtil(apiSettings.getAccessToken().getJwtSecret(), apiSettings.getAccessToken().getJwtExpiration());
	}

	/**
	 * Parse JWT if exists
	 * Store UserAuth info to session-scoped bean AuthInfo
	 *
	 * @param request
	 * @param response
	 * @param handler
	 * @return
	 * @throws Exception
	 */
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		try {
			String token = request.getHeader(tokenHeader);
			if(fixedUserId == null && (token == null || token.isEmpty())) {
				throw new AccessTokenNotFoundException();
			} else {
				try {
					Long userId = (fixedUserId != null) ? fixedUserId : jwtService.getUserIdFromJWT(token);
					loadUserInfo(userId);
				} catch(JwtValidateRuntimeException ex) {
					// Invalid JWT signature
					throw new AccessTokenValidationException("Invalid AccessToken", ex);
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

	private void loadUserInfo(long userId) {
		User user = dslContext.selectFrom(USER)
				.where(USER.ID.eq(userId))
				.fetchOptionalInto(User.class)
				.orElseThrow(() -> new AuthenticationException("User not found"));

		authInfo.setUser(user);
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

	}
}
