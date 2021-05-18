package io.talken.dex.shared.service.integration.wallet;

import io.talken.common.util.integration.rest.StringRestApiResponse;

/**
 * The type Talken wallet raw response.
 */
public class TalkenWalletRawResponse extends StringRestApiResponse {
	@Override
	public boolean checkHttpResponse(int httpStatus) {
		return true; // always true
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
