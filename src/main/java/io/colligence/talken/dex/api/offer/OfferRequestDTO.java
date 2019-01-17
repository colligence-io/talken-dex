package io.colligence.talken.dex.api.offer;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
public class OfferRequestDTO {
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
}
