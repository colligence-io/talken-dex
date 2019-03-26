package io.colligence.talken.dex.exception;

import io.colligence.talken.dex.service.integration.APIResult;

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
