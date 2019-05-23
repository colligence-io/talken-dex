package io.talken.dex.shared.service.blockchain.luniverse.dto;

import lombok.Data;

@Data
public class LuniverseWalletResponse extends LuniverseResponse {
	private Body data;

	@Data
	public static class Body {
		private String address;
	}
}