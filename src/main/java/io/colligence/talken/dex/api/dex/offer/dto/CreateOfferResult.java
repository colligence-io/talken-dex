package io.colligence.talken.dex.api.dex.offer.dto;

import io.colligence.talken.dex.api.dex.TxInformation;
import lombok.Data;

@Data
public class CreateOfferResult {
	private String taskId;
	private TxInformation txInformation;
	private double feeAmount;
	private String feeAssetType;
}
