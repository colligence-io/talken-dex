package io.colligence.talken.dex.api.dex.misc.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
public class AssetConvertRequest {
	@NotEmpty
	private String from;
	@NotNull
	private Double amount;
	@NotEmpty
	private String to;
}
