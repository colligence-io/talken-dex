package io.talken.dex.shared.service.blockchain.luniverse.dto;

import io.talken.dex.shared.service.blockchain.luniverse.LuniverseApiClient;
import lombok.Data;

@Data
public class LuniverseWalletRequest {
	private final String walletType;
	private String userKey;

	public LuniverseWalletRequest(String walletType, String userKey) {
		this.walletType = walletType;
		this.userKey = userKey;
	}
}
