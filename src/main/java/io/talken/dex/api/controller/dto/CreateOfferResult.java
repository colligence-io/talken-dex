package io.talken.dex.api.controller.dto;

import lombok.Data;

@Data
public class CreateOfferResult {
	private String taskId;
	private String transId;
	private String sellAssetCode;
	private Double sellAmount;
	private Double sellPrice;
	private String buyAssetCode;
	private String feeAssetCode;
	private Double feeAmount;
}
