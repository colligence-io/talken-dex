package io.talken.dex.governance.service.integration.signer;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SignServerIntroduceResponse extends AbstractSignServerResponse<SignServerIntroduceResponse._Data> {
	private _Data data;

	@Data
	public static class _Data {
		private String question;
		private String expires;
	}
}
