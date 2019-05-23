package io.talken.dex.shared.service.blockchain.luniverse.dto;

import io.talken.dex.shared.service.blockchain.luniverse.LuniverseApiClient;
import lombok.Data;

@Data
public class LuniverseWalletRequest {
	private final String walletType = LuniverseApiClient.LUNIVERSE_PKMS_WALLET;
	private String userKey;

	public LuniverseWalletRequest(String userKey) {
		this.userKey = userKey;
	}
}
