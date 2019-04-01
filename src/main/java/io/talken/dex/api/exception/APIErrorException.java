package io.talken.dex.api.exception;

import io.talken.dex.api.service.integration.APIResult;

public class APIErrorException extends DexException {
	private static final long serialVersionUID = 7055335221308754388L;

	private APIResult apiResult;

	public APIErrorException(APIResult apiResult) {
		super(DexExceptionTypeEnum.API_RETURNED_ERROR, apiResult.getApiName(), apiResult.getErrorCode());
		this.apiResult = apiResult;
	}

	public APIResult getApiResult() {
		return apiResult;
	}
}
