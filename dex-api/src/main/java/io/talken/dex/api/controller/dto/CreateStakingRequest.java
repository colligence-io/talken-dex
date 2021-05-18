package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * The type Create staking request.
 */
@Data
public class CreateStakingRequest {
	@NotEmpty
	private String stakingCode;
	@NotEmpty
	private String stakingAssetCode;
	@NotNull
	private BigDecimal amount;
}
