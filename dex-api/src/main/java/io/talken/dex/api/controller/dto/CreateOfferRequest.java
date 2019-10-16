package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class CreateOfferRequest {
	@NotEmpty
	private String sellAssetCode;
	@NotEmpty
	private String buyAssetCode;
	@NotNull
	private BigDecimal amount;
	@NotNull
	private BigDecimal price;
	private Boolean feeByTalk;

	public Boolean getFeeByTalk() {
		return (feeByTalk != null) ? feeByTalk : false;
	}
}
