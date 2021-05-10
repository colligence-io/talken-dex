package io.talken.dex.api.controller.dto;

import io.talken.common.persistence.enums.DexTaskTypeEnum;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OfferDetailResult {
	private String taskId;
	private DexTaskTypeEnum taskType;
	private long offerId;
	private String txHash;
	private String sellAssetCode;
	private String buyAssetCode;
	private BigDecimal amount;
	private BigDecimal price;
}