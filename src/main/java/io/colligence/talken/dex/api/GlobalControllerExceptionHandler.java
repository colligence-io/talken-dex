package io.colligence.talken.dex.api;


import io.colligence.talken.common.CLGException;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.exception.APIErrorException;
import io.colligence.talken.dex.exception.InternalServerErrorException;
import io.colligence.talken.dex.exception.UnauthorizedException;
import io.colligence.talken.dex.service.MessageService;
import io.colligence.talken.dex.service.integration.APIError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
	MessageService ms;

	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	@ExceptionHandler(UnauthorizedException.class)
	@ResponseBody
	public DexResponse<Void> handleRuntimeException(UnauthorizedException e, Locale locale) {
		return DexResponse.buildResponse(new DexResponseBody<>(e.getCode(), ms.getMessage(locale, e), HttpStatus.UNAUTHORIZED, null));
	}

	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(APIErrorException.class)
	@ResponseBody
	public DexResponse<APIError> handleCLGException(APIErrorException e, Locale locale) {
		return DexResponse.buildResponse(new DexResponseBody<>(e.getCode(), ms.getMessage(locale, e), HttpStatus.INTERNAL_SERVER_ERROR, e.getApiError()));
	}

	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(CLGException.class)
	@ResponseBody
	public DexResponse<Void> handleCLGException(CLGException e, Locale locale) {
		return DexResponse.buildResponse(new DexResponseBody<>(e.getCode(), ms.getMessage(locale, e), HttpStatus.INTERNAL_SERVER_ERROR, null));
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
