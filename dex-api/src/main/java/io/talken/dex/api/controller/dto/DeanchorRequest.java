package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * The type Deanchor request.
 */
@Data
public class DeanchorRequest {
	@NotEmpty
	private String privateWalletAddress;
	@NotEmpty
	private String assetCode;
	@NotNull
	private BigDecimal amount;
}
