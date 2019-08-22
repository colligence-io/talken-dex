package io.talken.dex.shared.service.integration.anchor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.talken.common.util.integration.rest.RestApiResponseInterface;
import lombok.Data;

@Data
@JsonIgnoreProperties("message")
public class AncServerDeanchorResponse implements RestApiResponseInterface {
	private String code;
	private String description;
	private _Data data;

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
		return getDescription();
	}

	@Data
	public static class _Data {
		private Integer index;
	}
}
