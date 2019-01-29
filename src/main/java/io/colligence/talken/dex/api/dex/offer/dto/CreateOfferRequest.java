package io.colligence.talken.dex.api.dex.offer.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
public class CreateOfferRequest {
	@NotEmpty
	private String sourceAccountId;
	@NotEmpty
	private String sellAssetCode;
	@NotEmpty
	private String buyAssetCode;
	@NotNull
	private Double sellAssetAmount;
	@NotNull
	private Double sellAssetPrice;
	private Boolean feeByCtx;

	public Boolean getFeeByCtx() {
		return (feeByCtx != null) ? feeByCtx : false;
	}
}
