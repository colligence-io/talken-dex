package io.colligence.talken.dex.api.dex.anchor.dto;

import lombok.Data;

@Data
public class AnchorResult {
	private String taskId;
	private String transId;
	private String assetType;
	private Double amount;
	private String holderAccountAddress;
}
