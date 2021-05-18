package io.talken.dex.shared.service.integration.signer;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * The type Sign server answer response.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SignServerAnswerResponse extends AbstractSignServerResponse<SignServerAnswerResponse._Data> {
    /**
     * The type Data.
     */
    @Data
	public static class _Data {
		private String welcomePresent;
		private Map<String, String> welcomePackage;
		private Long expires;
	}
}
