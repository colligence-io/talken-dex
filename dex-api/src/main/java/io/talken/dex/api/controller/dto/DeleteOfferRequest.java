package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class DeleteOfferRequest {
	@NotNull
	private Long offerId;
	@NotEmpty
	private String sellAssetCode;
	@NotEmpty
	private String buyAssetCode;
	@NotNull
	private BigDecimal price;
}
