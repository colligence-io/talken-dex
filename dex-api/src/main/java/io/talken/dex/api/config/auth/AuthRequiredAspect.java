package io.talken.dex.api.config.auth;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.exception.UnauthorizedException;
import io.talken.dex.shared.exception.auth.AuthenticationException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The type Auth required aspect.
 */
@Component
@Aspect
@Order(0)
public class AuthRequiredAspect {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AuthRequiredAspect.class);

	@Autowired
	private AuthInfo authInfo;

    /**
     * Is auth required.
     *
     * @param authRequired the auth required
     */
//	@Pointcut("execution(@AuthRequired * *(..))")
	@Pointcut(value = "@annotation(authRequired)")
	public void isAuthRequired(AuthRequired authRequired) {}

    /**
     * Validate auth object.
     *
     * @param joinPoint    the join point
     * @param authRequired the auth required
     * @return the object
     * @throws Throwable the throwable
     */
    @Around("isAuthRequired(authRequired)")
	public Object validateAuth(ProceedingJoinPoint joinPoint, AuthRequired authRequired) throws Throwable {
		if(authInfo == null)
			throw new UnauthorizedException(new AuthenticationException("Authentication information not found."));
		authInfo.checkAuth();
		return joinPoint.proceed();
	}
}
