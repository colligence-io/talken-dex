package io.talken.dex.service.integration.signer;

import io.talken.dex.service.integration.CodeMessageResponseInterface;
import lombok.Data;

@Data
public class SignServerSignResponse implements CodeMessageResponseInterface {
	private int code;
	private String message;
	private SignServerSignResponse._Data data;

	@Override
	public boolean isSuccess() {
		return code == 200;
	}

	@Data
	public static class _Data {
		private String signature;
	}
}
