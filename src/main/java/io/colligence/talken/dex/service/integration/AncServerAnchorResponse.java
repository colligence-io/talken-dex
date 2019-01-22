package io.colligence.talken.dex.service.integration;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AncServerAnchorResponse {
	private int code;
	private String description;
	private _Data data;

	@Getter
	@Setter
	public static class _Data {
		private Integer index;
		private String address;
	}
}
