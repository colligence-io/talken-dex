package io.talken.dex.api.controller.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SwapPredictionResult {
	private String sourceAssetCode;
	private String targetAssetCode;
	private BigDecimal amount;
	private BigDecimal feeAmount;
	private BigDecimal prediction;
}
