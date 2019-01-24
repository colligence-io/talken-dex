package io.colligence.talken.dex.api.dex.anchor.dto;

import io.colligence.talken.dex.api.dex.TxInformation;
import lombok.Data;

@Data
public class DeanchorResult {
	private String taskID;
	private String feeAssetType;
	private Double feeAmount;
	private String deanchorAssetType;
	private Double deanchorAmount;
	private TxInformation txInformation;
}
