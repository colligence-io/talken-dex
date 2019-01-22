package io.colligence.talken.dex.api.dex.anchor.dto;

import io.colligence.talken.dex.service.integration.txTunnel.TxtServerResponse;
import lombok.Data;

@Data
public class AnchorSubmitResult {
	private TxtServerResponse txtServerResponse;

	public AnchorSubmitResult(TxtServerResponse txtServerResponse) {
		this.txtServerResponse = txtServerResponse;
	}
}
