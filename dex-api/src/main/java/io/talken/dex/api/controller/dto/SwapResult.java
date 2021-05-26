package io.talken.dex.api.controller.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * The type Swap result.
 */
@Data
public class SwapResult {
	private String taskId;
	private String transId;
	private String sourceAssetCode;
	private BigDecimal sourceAmount;
	private String targetAssetCode;
	private BigDecimal targetAmount;
	private String holderAccountAddress;
	private String swapperAccountAddress;
}
