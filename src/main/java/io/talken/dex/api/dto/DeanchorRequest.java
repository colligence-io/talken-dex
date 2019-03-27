package io.talken.dex.api.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
public class DeanchorRequest {
	@NotEmpty
	private String privateWalletAddress;
	@NotEmpty
	private String tradeWalletAddress;
	@NotEmpty
	private String assetCode;
	@NotNull
	private Double amount;
	private Boolean feeByCtx;

	public Boolean getFeeByCtx() {
		return (feeByCtx != null) ? feeByCtx : false;
	}
}
