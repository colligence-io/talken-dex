package io.colligence.talken.dex.service.integration.signer;

import lombok.Data;

@Data
public class SignServerSignRequest {
	private String keyID;
	private String type;
	private String address;
	private String data;
}
