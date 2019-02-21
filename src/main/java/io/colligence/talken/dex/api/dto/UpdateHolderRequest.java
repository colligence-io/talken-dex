package io.colligence.talken.dex.api.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

@Data
public class UpdateHolderRequest {
	@NotEmpty
	private String address;
	private Boolean isHot;
	private Boolean isActive;
}
