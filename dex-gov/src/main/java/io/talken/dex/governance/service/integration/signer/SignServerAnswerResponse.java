package io.talken.dex.governance.service.integration.signer;

import io.talken.dex.shared.service.integration.CodeMessageResponseInterface;
import lombok.Data;

import java.util.Map;

@Data
public class SignServerAnswerResponse implements CodeMessageResponseInterface {
	private String code;
	private String message;
	private _Data data;

	@Override
	public boolean isSuccess() {
		return "200".equals(code);
	}

	@Data
	public static class _Data {
		private String welcomePresent;
		private Map<String, String> welcomePackage;
		private Long expires;
	}
}
