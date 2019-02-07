package io.colligence.talken.dex.api.dex.anchor.dto;

import lombok.Data;

@Data
public class DeanchorResult {
	private String taskId;
	private String transId;
	private String feeAssetCode;
	private Double feeAmount;
	private String deanchorAssetCode;
	private Double deanchorAmount;
}
