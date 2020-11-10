package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

@Data
public class PendingTxListRequest {
	@NotEmpty
	private String address;
    @NotEmpty
	private String symbol;
}
