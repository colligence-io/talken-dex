package io.talken.dex.shared.service.blockchain.luniverse.dto;

import lombok.Data;

@Data
public class LuniverseWalletBalanceResponse extends LuniverseResponse {
	private Body data;

	@Data
	public static class Body {
		private String balance;
	}
}