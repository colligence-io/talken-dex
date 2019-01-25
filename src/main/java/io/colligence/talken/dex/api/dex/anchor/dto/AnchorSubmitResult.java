package io.colligence.talken.dex.api.dex.anchor.dto;

import io.colligence.talken.dex.service.integration.txTunnel.TxtServerResponse;
import lombok.Data;

@Data
public class AnchorSubmitResult {
	private TxtServerResponse transaction;

	public AnchorSubmitResult(TxtServerResponse txtServerResponse) {
		this.transaction = txtServerResponse;
	}
}
