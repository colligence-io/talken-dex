package io.talken.dex.shared.service.integration.signer;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SignServerIntroduceResponse extends AbstractSignServerResponse<SignServerIntroduceResponse._Data> {
	@Data
	public static class _Data {
		private String question;
		private String expires;
	}
}
