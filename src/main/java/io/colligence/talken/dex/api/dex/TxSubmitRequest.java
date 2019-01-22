package io.colligence.talken.dex.api.dex;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

@Data
public class TxSubmitRequest {
	@NotEmpty
	private String taskID;
	@NotEmpty
	private String txHash;
	@NotEmpty
	private String txEnvelope;
}
