package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

/**
 * The type Klay transaction list request.
 */
@Data
public class KlayTransactionListRequest {
	@NotEmpty
	private String address;
    private String contract;
    @NotEmpty
    private String type;
    private String cursor;
}
