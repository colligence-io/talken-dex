package io.talken.dex.api.controller.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AnchorResult {
	private String taskId;
	private String transId;
	private String assetCode;
	private BigDecimal amount;
	private String holderAccountAddress;
}
