package io.talken.dex.shared.service.blockchain.luniverse.dto;


import io.talken.common.util.integration.RestApiResponseInterface;
import lombok.Data;

@Data
public class LuniverseResponse implements RestApiResponseInterface {
	private boolean result;
	private String code;
	private String message;

	@Override
	public boolean isSuccess() {
		return result;
	}
}