package io.colligence.talken.dex.api.dex.anchor.dto;

import io.colligence.talken.dex.service.integration.anchor.AncServerDeanchorResponse;
import io.colligence.talken.dex.service.integration.txTunnel.TxtServerResponse;
import lombok.Data;

@Data
public class DeanchorSubmitResult {
	private TxtServerResponse transaction;
	private AncServerDeanchorResponse deanchor;

	public DeanchorSubmitResult(TxtServerResponse txtServerResponse, AncServerDeanchorResponse deanchorResponse) {
		this.transaction = txtServerResponse;
		this.deanchor = deanchorResponse;
	}

	public boolean getCriticalError() {
		if(transaction != null && transaction.isSuccess() == true) {
			if(deanchor == null || !deanchor.isSuccess()) {
				return true;
			}
		}
		return false;
	}
}
