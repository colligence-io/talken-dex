package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * The type Asset convert request.
 */
@Data
public class AssetConvertRequest {
	@NotEmpty
	private String from;
	@NotNull
	private BigDecimal amount;
	@NotEmpty
	private String to;
}
