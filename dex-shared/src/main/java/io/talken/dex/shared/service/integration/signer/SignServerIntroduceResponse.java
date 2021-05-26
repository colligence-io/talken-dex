package io.talken.dex.shared.service.integration.signer;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * The type Sign server introduce response.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SignServerIntroduceResponse extends AbstractSignServerResponse<SignServerIntroduceResponse._Data> {
    /**
     * The type Data.
     */
    @Data
	public static class _Data {
		private String question;
		private String expires;
	}
}
