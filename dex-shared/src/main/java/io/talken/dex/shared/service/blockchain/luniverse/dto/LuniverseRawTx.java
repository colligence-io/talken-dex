package io.talken.dex.shared.service.blockchain.luniverse.dto;

import lombok.Data;

@Data
public class LuniverseRawTx {
	private String from;
	private String to;
	private String data;
	private String nonce;
	private String gasLimit;
	private String gasPrice;
	private Integer chainId;
}
