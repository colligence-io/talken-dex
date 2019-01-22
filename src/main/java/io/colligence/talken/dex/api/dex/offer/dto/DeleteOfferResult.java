package io.colligence.talken.dex.api.dex.offer.dto;

import io.colligence.talken.dex.api.dex.TxInformation;
import lombok.Data;

@Data
public class DeleteOfferResult {
	private TxInformation txInformation;

	public DeleteOfferResult(TxInformation txInformation) {
		this.txInformation = txInformation;
	}
}
