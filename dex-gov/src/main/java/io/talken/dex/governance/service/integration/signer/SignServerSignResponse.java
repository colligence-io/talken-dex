package io.talken.dex.governance.service.integration.signer;

import io.talken.common.util.integration.RestApiResponseInterface;
import lombok.Data;

@Data
public class SignServerSignResponse implements RestApiResponseInterface {
	private String code;
	private String message;
	private _Data data;

	@Override
	public boolean isSuccess() {
		return "200".equals(code);
	}

	@Data
	public static class _Data {
		private String signature;
	}
}
