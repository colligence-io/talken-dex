package io.talken.dex.api.controller.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * The type Deanchor result.
 */
@Data
public class DeanchorResult {
	private String taskId;
	private String txHash;
	private String feeAssetCode;
	private BigDecimal feeAmount;
	private String deanchorAssetCode;
	private BigDecimal deanchorAmount;
}
