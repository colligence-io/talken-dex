package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.DexException;
import io.colligence.talken.dex.DexExceptionType;
import io.colligence.talken.dex.service.integration.APIError;

public class APIErrorException extends DexException {
	private static final long serialVersionUID = 7055335221308754388L;

	private APIError apiError;

	public APIErrorException(APIError apiError) {
		super(DexExceptionType.API_RETURNED_ERROR, apiError.getApiName(), apiError.getCode());
		this.apiError = apiError;
	}

	public APIError getApiError() {
		return apiError;
	}
}
