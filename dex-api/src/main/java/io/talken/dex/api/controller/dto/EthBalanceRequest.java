package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

/**
 * The type Eth balance request.
 */
@Data
public class EthBalanceRequest {
	@NotEmpty
	private String address;
}
