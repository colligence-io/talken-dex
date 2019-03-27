package io.talken.dex.service.integration.signer;

import lombok.Data;

@Data
public class SignServerSignRequest {
	private String type;
	private String address;
	private String data;
	private String answer;
}
