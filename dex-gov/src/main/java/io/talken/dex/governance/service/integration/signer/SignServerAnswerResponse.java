package io.talken.dex.governance.service.integration.signer;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class SignServerAnswerResponse extends AbstractSignServerResponse<SignServerAnswerResponse._Data> {
	private _Data data;

	@Data
	public static class _Data {
		private String welcomePresent;
		private Map<String, String> welcomePackage;
		private Long expires;
	}
}
