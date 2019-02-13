package io.colligence.talken.dex.api.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
public class DeleteOfferRequest {
	@NotEmpty
	private Long offerId;
	@NotEmpty
	private String tradeWalletAddress;
	@NotEmpty
	private String sellAssetCode;
	@NotEmpty
	private String buyAssetCode;
	@NotNull
	private Double sellAssetPrice;
}
