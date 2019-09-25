package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

@Data
public class EthBalanceRequest {
	@NotEmpty
	private String address;
}
