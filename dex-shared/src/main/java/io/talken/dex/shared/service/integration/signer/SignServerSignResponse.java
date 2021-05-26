package io.talken.dex.shared.service.integration.signer;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * The type Sign server sign response.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SignServerSignResponse extends AbstractSignServerResponse<SignServerSignResponse._Data> {
    /**
     * The type Data.
     */
    @Data
	public static class _Data {
		private String signature;
	}
}
