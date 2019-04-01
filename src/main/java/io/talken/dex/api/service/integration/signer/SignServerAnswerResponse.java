package io.talken.dex.api.service.integration.signer;

import io.talken.dex.api.service.integration.CodeMessageResponseInterface;
import lombok.Data;

import java.util.Map;

@Data
public class SignServerAnswerResponse implements CodeMessageResponseInterface {
	private int code;
	private String message;
	private SignServerAnswerResponse._Data data;

	@Override
	public boolean isSuccess() {
		return code == 200;
	}

	@Data
	public static class _Data {
		private String welcomePresent;
		private Map<String, String> welcomePackage;
		private Long expires;
	}
}