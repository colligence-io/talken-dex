package io.colligence.talken.dex.service.integration.anchor;

import lombok.Data;

@Data
public class AncServerDeanchorResponse {
	private int code;
	private String description;

	public boolean isSuccess() {
		return code == 200;
	}
}
