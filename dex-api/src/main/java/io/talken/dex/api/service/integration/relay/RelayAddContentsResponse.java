package io.talken.dex.api.service.integration.relay;

import io.talken.common.util.integration.rest.RestApiResponseInterface;
import lombok.Data;

import java.time.LocalDateTime;

@Deprecated
@Data
public class RelayAddContentsResponse implements RestApiResponseInterface {
	private String transId;
	private String status;
	private LocalDateTime regDt;
	private LocalDateTime endDt;

	@Override
	public boolean checkHttpResponse(int httpStatus) {
		return RestApiResponseInterface.standardHttpSuccessCheck(httpStatus);
	}

	@Override
	public boolean checkResult() {
		return false;
	}

	@Override
	public String resultCode() {
		return status;
	}

	@Override
	public String resultMessage() {
		return null;
	}
}
