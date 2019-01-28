package io.colligence.talken.dex.api.dex.anchor.dto;

import io.colligence.talken.dex.api.dex.TxSubmitResult;
import io.colligence.talken.dex.service.integration.anchor.AncServerDeanchorResponse;
import io.colligence.talken.dex.service.integration.txTunnel.TxtServerResponse;
import lombok.Data;

@Data
public class DeanchorSubmitResult {
	private AncServerDeanchorResponse deanchorResponse;
	private TxSubmitResult txSubmitResult;

	public DeanchorSubmitResult(AncServerDeanchorResponse deanchorResponse, TxSubmitResult txSubmitResult) {
		this.deanchorResponse = deanchorResponse;
		this.txSubmitResult = txSubmitResult;
	}
}
