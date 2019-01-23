package io.colligence.talken.dex.config.auth;

import io.colligence.talken.common.RunningProfile;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.exception.UnauthorizedException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Aspect
@Order(0)
public class AuthRequiredAspect {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AuthRequiredAspect.class);

	@Autowired
	private AuthInfo authInfo;

	//	@Pointcut("execution(@AuthRequired * *(..))")
	@Pointcut(value = "@annotation(authRequired)")
	public void isAuthRequired(AuthRequired authRequired) {}

	@Around("isAuthRequired(authRequired)")
	public Object validateAuth(ProceedingJoinPoint joinPoint, AuthRequired authRequired) throws Throwable {
		if(authInfo != null) authInfo.getUserId();
		else throw new UnauthorizedException();
		return joinPoint.proceed();
	}
}
