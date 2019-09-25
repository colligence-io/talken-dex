package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

@Data
public class Erc20BalanceRequest {
	@NotEmpty
	private String contract;
	@NotEmpty
	private String address;
}
