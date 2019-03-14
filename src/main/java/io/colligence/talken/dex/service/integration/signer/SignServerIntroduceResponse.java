package io.colligence.talken.dex.service.integration.signer;

import io.colligence.talken.dex.service.integration.CodeMessageResponseInterface;
import lombok.Data;

@Data
public class SignServerIntroduceResponse implements CodeMessageResponseInterface {
	private int code;
	private String message;
	private SignServerIntroduceResponse._Data data;

	@Override
	public boolean isSuccess() {
		return code == 200;
	}

	@Data
	public static class _Data {
		private String question;
		private String expires;
	}
}
