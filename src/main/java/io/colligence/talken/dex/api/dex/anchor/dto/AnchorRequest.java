package io.colligence.talken.dex.api.dex.anchor.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
public class AnchorRequest {
	@NotEmpty
	private String privateWalletAddress;
	@NotEmpty
	private String tradeWalletAddress;
	@NotEmpty
	private String assetCode;
	@NotNull
	private Double amount;
}
