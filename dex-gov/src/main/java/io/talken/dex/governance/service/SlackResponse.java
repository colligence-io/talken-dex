package io.talken.dex.governance.service;


import io.talken.common.util.integration.rest.RestApiResponseInterface;
import io.talken.common.util.integration.rest.StringRestApiResponse;

public class SlackResponse extends StringRestApiResponse {
	@Override
	public boolean checkHttpResponse(int httpStatus) {
		return RestApiResponseInterface.standardHttpSuccessCheck(httpStatus);
	}

	@Override
	public boolean checkResult() {
		return true;
	}

	@Override
	public String resultCode() {
		return "";
	}

	@Override
	public String resultMessage() {
		return getData();
	}
}
