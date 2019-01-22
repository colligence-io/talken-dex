package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;
import lombok.Getter;
import lombok.Setter;

public class APIErrorException extends DexException {
	private static final long serialVersionUID = 7055335221308754388L;

	private APIError apiError;

	public APIErrorException(String apiName, String code, String message, Object data) {
		this(apiName, new APIError(code, message, data));
	}

	public APIErrorException(String apiName, APIError apiError) {
		super(DexExceptionType.API_RETURNED_ERROR, apiName, apiError.code);
		this.apiError = apiError;
	}

	public APIError getApiError() {
		return apiError;
	}

	@Getter
	@Setter
	public static class APIError {
		private String code;
		private String message;
		private Object rawResult;

		public APIError(String code, String message, Object rawResult) {
			this.code = code;
			this.message = message;
			this.rawResult = rawResult;
		}
	}
}
