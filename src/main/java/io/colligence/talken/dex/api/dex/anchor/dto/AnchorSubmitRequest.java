package io.colligence.talken.dex.api.dex.anchor.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

@Data
public class AnchorSubmitRequest {
	@NotEmpty
	private String taskId;
	@NotEmpty
	private String assetCode;
	@NotEmpty
	private String txData;
}
