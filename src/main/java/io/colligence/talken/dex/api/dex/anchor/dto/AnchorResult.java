package io.colligence.talken.dex.api.dex.anchor.dto;

import lombok.Data;

@Data
public class AnchorResult {
	private String taskId;
	private String transId;
	private String assetCode;
	private Double amount;
	private String holderAccountAddress;
}
