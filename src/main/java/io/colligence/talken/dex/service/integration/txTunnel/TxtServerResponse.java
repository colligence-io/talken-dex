package io.colligence.talken.dex.service.integration.txTunnel;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TxtServerResponse {
	private boolean success;
	private String code;
	private String message;
	private String hash;
	private String payload;
}
