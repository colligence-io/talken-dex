package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class DeanchorRequest {
	@NotEmpty
	private String privateWalletAddress;
	@NotEmpty
	private String tradeWalletAddress;
	@NotEmpty
	private String assetCode;
	@NotNull
	private BigDecimal amount;
	private Boolean feeByTalk;
	@NotNull
	private BigDecimal networkFee;

	public Boolean getFeeByTalk() {
		return (feeByTalk != null) ? feeByTalk : false;
	}
}
