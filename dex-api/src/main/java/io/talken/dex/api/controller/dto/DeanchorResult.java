package io.talken.dex.api.controller.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DeanchorResult {
	private String taskId;
	private String feeAssetCode;
	private BigDecimal feeAmount;
	private String deanchorAssetCode;
	private BigDecimal deanchorAmount;
}
