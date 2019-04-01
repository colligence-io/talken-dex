package io.talken.dex.api.service.integration.txTunnel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.talken.dex.api.service.integration.CodeMessageResponseInterface;
import lombok.Data;

@Data
@JsonIgnoreProperties({"payload"})
public class TxtServerResponse implements CodeMessageResponseInterface {
	private boolean success;
	@JsonProperty(value = "code")
	private String rCode;
	private String message;
	private String hash;
	private String payload;

	// fixed
	@Override
	public int getCode() {
		return 0;
	}
}
