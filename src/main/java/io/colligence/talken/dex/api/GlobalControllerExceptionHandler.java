package io.colligence.talken.dex.api;


import io.colligence.talken.common.CLGException;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.service.MessageService;
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

	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(CLGException.class)
	@ResponseBody
	public DexResponse<Void> handleCLGException(CLGException e, Locale locale) {

		ms.getMessage(locale, e);

		return DexResponse.buildExceptionResponse(e, ms.getMessage(locale, e));
	}

	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(Exception.class)
	@ResponseBody
	public DexResponse<Void> handleCLGException(Exception e, Locale locale) {
//		if(e instanceof DataIntegrityViolationException) {
//			// TODO : jooq datatbase exeption
//			logger.exception(e);
//			if(!RunningProfile.getRunningProfile().equals(CommonConsts.RUNNING_PROFILE.LOCAL)) {
//				return new DexResponse(, );
//			}
//		}
		e.printStackTrace();
		return DexResponse.buildExceptionResponse(e, "RuntimeException");
	}
}
