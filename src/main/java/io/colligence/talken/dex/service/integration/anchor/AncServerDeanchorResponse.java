package io.colligence.talken.dex.service.integration.anchor;

import lombok.Data;

@Data
public class AncServerDeanchorResponse {
	private int code;
	private String description;
	private _Data data;

	private boolean isSuccess() {
		return code == 200;
	}

	@Data
	public static class _Data {
		private Integer index;
		private String address;
	}
}
