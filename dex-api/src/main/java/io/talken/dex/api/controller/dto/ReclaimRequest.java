package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class ReclaimRequest {
	@NotEmpty
	private String assetCode;
	@NotNull
	private BigDecimal amount;
	private BigDecimal fee;
}
