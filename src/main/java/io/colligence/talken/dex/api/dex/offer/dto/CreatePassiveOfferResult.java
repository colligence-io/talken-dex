package io.colligence.talken.dex.api.dex.offer.dto;

import io.colligence.talken.dex.api.dex.TxInformation;
import lombok.Data;

@Data
public class CreatePassiveOfferResult {
	private TxInformation txInformation;

	public CreatePassiveOfferResult(TxInformation txInformation) {
		this.txInformation = txInformation;
	}
}
