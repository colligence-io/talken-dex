package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class AnchorRequest {
	@NotEmpty
	private String type;
	@NotEmpty
	private String coin;
	@NotEmpty
	private String symbol;
	@NotNull
	private BigDecimal amount;
	@NotEmpty
	private String from;

	private String to;
	@NotEmpty
	private String stellar;

	private _Asset asset;
	private String contract;

	@Data
	public static class _Asset {
		private String issuer;
		private String code;
	}
}
