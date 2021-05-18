package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

/**
 * The type Pending tx list request.
 */
@Data
public class PendingTxListRequest {
	@NotEmpty
	private String address;
    @NotEmpty
	private String symbol;
}
