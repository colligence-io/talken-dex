package io.colligence.talken.dex.service.integration.txTunnel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties({"payload"})
public class TxtServerResponse {
	private boolean success;
	private String code;
	private String message;
	private String hash;
	private String payload;
}
