package io.colligence.talken.dex.api.dex.offer.dto;

import io.colligence.talken.dex.api.dex.TxInformation;
import lombok.Data;

@Data
public class CreateOfferResult {
	private TxInformation txInformation;

	public CreateOfferResult(TxInformation txInformation) {
		this.txInformation = txInformation;
	}
}
