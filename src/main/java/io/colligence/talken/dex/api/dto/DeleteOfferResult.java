package io.colligence.talken.dex.api.dto;

import lombok.Data;

@Data
public class DeleteOfferResult {
	private String taskId;
	private String transId;
	private Long offerId;
	private String sellAssetCode;
	private Double sellAmount;
	private Double sellPrice;
}
