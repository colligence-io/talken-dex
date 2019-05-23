package io.talken.dex.shared.service.blockchain.luniverse.dto;


import io.talken.dex.shared.service.integration.CodeMessageResponseInterface;
import lombok.Data;

@Data
public class LuniverseResponse implements CodeMessageResponseInterface {
	private boolean result;
	private String code;
	private String message;

	@Override
	public boolean isSuccess() {
		return result;
	}
}