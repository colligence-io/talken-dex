package io.talken.dex.api.controller.dto;

import io.talken.common.persistence.enums.DexTaskTypeEnum;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DeleteOfferResult {
	private String taskId;
	private DexTaskTypeEnum taskType;

	private Long offerId;

	private String sellAssetCode;
	private String buyAssetCode;
	private BigDecimal price;
}
