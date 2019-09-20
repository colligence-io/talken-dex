package io.talken.dex.shared.service.integration.signer;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SignServerSignResponse extends AbstractSignServerResponse<SignServerSignResponse._Data> {
	private _Data data;

	@Data
	public static class _Data {
		private String signature;
	}
}
