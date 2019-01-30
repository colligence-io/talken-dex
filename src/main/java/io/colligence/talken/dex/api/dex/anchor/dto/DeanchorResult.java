package io.colligence.talken.dex.api.dex.anchor.dto;

import lombok.Data;

@Data
public class DeanchorResult {
	private String taskID;
	private String transId;
	private String feeAssetType;
	private Double feeAmount;
	private String deanchorAssetType;
	private Double deanchorAmount;
}
