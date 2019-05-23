package io.talken.dex.shared.service.blockchain.luniverse.dto;

import lombok.Data;

@Data
public class LuniverseTransactionResponse extends LuniverseResponse {
	private Body data;

	@Data
	public static class Body {
		private String from;
		private LuniverseRawTx rawTx;
		private String txId;
		private String txHash;
		private Long reqTs;
	}
}
