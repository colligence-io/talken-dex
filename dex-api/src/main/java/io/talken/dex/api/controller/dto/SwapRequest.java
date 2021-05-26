package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * The type Swap request.
 */
@Data
public class SwapRequest {
	@NotEmpty
	private String privateSourceAddr;
	@NotEmpty
	private String privateTargetAddr;
	@NotEmpty
	private String tradeAddr;
	@NotEmpty
	private String sourceAssetCode;
	@NotNull
	private BigDecimal sourceAmount;
	@NotEmpty
	private String targetAssetCode;
	@NotNull
	private BigDecimal targetAmount;
	@NotNull
	private BigDecimal networkFee;
}
