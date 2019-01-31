package io.colligence.talken.dex.api.dex.offer.dto;

import lombok.Data;

@Data
public class CreateOfferResult {
	private String taskId;
	private String transId;
	private String sellAssetType;
	private Double sellAmount;
	private Double sellPrice;
	private String buyAssetType;
	private String feeAssetType;
	private Double feeAmount;
}
