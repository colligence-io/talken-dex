package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

/**
 * The type Erc 20 balance request.
 */
@Data
public class Erc20BalanceRequest {
	@NotEmpty
	private String contract;
	@NotEmpty
	private String address;
}
