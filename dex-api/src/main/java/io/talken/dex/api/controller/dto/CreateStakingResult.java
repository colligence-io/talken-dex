package io.talken.dex.api.controller.dto;

import io.talken.common.persistence.enums.DexTaskTypeEnum;
import lombok.Data;

import java.math.BigDecimal;

/**
 * The type Create staking result.
 */
@Data
public class CreateStakingResult {
	private String taskId;
	private DexTaskTypeEnum taskType;

	private String stakingCode;
	private String stakingAssetCode;
	private BigDecimal amount;
}
