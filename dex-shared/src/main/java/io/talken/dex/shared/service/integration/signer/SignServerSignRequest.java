package io.talken.dex.shared.service.integration.signer;

import lombok.Data;

/**
 * The type Sign server sign request.
 */
@Data
public class SignServerSignRequest {
	private String type;
	private String address;
	private String data;
	private String answer;
}
