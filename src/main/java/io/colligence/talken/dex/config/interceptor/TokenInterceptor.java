package io.colligence.talken.dex.config.interceptor;

import io.colligence.talken.common.util.JwtUtil;
import io.colligence.talken.common.util.PrefixedLogger;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TokenInterceptor implements HandlerInterceptor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TokenInterceptor.class);
	private static final String HEADER_AUTH = "X-Auth-Token";

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		logger.error("into token interceptor");
		String token = request.getHeader(HEADER_AUTH);
		if(!token.isEmpty()) {
			logger.error("TOKEN : {} ", token);
			return JwtUtil.validateToken(token);
		}

		response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
		return false;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

	}
}
