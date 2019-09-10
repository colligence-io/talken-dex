package io.talken.dex.api.controller;


import io.talken.common.exception.TalkenException;
import io.talken.common.exception.common.IntegrationException;
import io.talken.common.service.MessageService;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.IntegrationResult;
import io.talken.dex.shared.exception.InternalServerErrorException;
import io.talken.dex.shared.exception.ParameterViolationException;
import io.talken.dex.shared.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Locale;

@PrefixedLogger.NoStacktraceLogging
@ControllerAdvice
public class GlobalControllerExceptionHandler {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(GlobalControllerExceptionHandler.class);

	@Autowired
	private MessageService ms;

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public DexResponse<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e, Locale locale) {
		logger.exception(e);
		return DexResponse.buildResponse(new DexResponseBody<>(HttpStatus.BAD_REQUEST.value(), "Request Violation", HttpStatus.BAD_REQUEST, null));
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(ParameterViolationException.class)
	public DexResponse<Void> handleParameterViolationException(ParameterViolationException e, Locale locale) {
		logger.exception(e);
		return DexResponse.buildResponse(new DexResponseBody<>(HttpStatus.BAD_REQUEST.value(), "Parameter Violation", HttpStatus.BAD_REQUEST, null));
	}

	@ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	@ResponseBody
	public DexResponse<Void> handleMethodNotAllowedException(HttpRequestMethodNotSupportedException e, Locale locale) {
		logger.exception(e);
		return DexResponse.buildResponse(new DexResponseBody<>(HttpStatus.METHOD_NOT_ALLOWED.value(), e.getMessage(), HttpStatus.METHOD_NOT_ALLOWED, null));
	}

	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	@ExceptionHandler(UnauthorizedException.class)
	@ResponseBody
	public DexResponse<Void> handleRuntimeException(UnauthorizedException e, Locale locale) {
		return DexResponse.buildResponse(new DexResponseBody<>(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(), HttpStatus.UNAUTHORIZED, null));
	}

	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(IntegrationException.class)
	@ResponseBody
	public DexResponse<IntegrationResult> handleCLGException(IntegrationException e, Locale locale) {
		// FIXME : e.getApiResult is too sensitive to return as result body, consider hide it
		logger.exception(e);
		return DexResponse.buildResponse(new DexResponseBody<>(e.getErrorCode(), ms.getMessage(locale, e), HttpStatus.INTERNAL_SERVER_ERROR, e.getResult()));
	}

	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(TalkenException.class)
	@ResponseBody
	public DexResponse<Void> handleCLGException(TalkenException e, Locale locale) {
		logger.exception(e);
		return DexResponse.buildResponse(new DexResponseBody<>(e.getErrorCode(), ms.getMessage(locale, e), HttpStatus.INTERNAL_SERVER_ERROR, null));
	}

	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(RuntimeException.class)
	@ResponseBody
	public DexResponse<Void> handleRuntimeException(RuntimeException e, Locale locale) {
//		if(e instanceof DataIntegrityViolationException) {
//			// TODO : jooq datatbase exeption
//			logger.exception(e);
//			if(!RunningProfile.getRunningProfile().equals(CommonConsts.RUNNING_PROFILE.LOCAL)) {
//				return new DexResponse(, );
//			}
//		}
		logger.exception(e);
		return handleCLGException(new InternalServerErrorException(e), locale);
	}

	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(Exception.class)
	@ResponseBody
	public DexResponse<Void> handleException(Exception e, Locale locale) {
		logger.exception(e);
		return handleCLGException(new InternalServerErrorException(e), locale);
	}
}
