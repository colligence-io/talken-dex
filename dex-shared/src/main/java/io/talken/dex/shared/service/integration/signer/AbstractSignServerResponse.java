package io.talken.dex.shared.service.integration.signer;

import io.talken.common.util.integration.rest.RestApiResponseInterface;
import lombok.Data;

@Data
public abstract class AbstractSignServerResponse<T> implements RestApiResponseInterface {
	private String code;
	private String message;
	private T data;

	@Override
	public boolean checkHttpResponse(int httpStatus) {
		return RestApiResponseInterface.standardHttpSuccessCheck(httpStatus);
	}

	@Override
	public boolean checkResult() {
		return "200".equals(code);
	}

	@Override
	public String resultCode() {
		return code;
	}

	@Override
	public String resultMessage() {
		return message;
	}
}
