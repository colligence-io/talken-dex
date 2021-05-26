package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * The type Create offer request.
 */
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
}
