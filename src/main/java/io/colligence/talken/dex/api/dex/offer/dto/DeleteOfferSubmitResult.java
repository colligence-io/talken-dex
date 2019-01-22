package io.colligence.talken.dex.api.dex.offer.dto;

import io.colligence.talken.dex.api.dex.TxSubmitResult;
import lombok.Data;

@Data
public class DeleteOfferSubmitResult {
	private TxSubmitResult txSubmitResult;

	public DeleteOfferSubmitResult(TxSubmitResult txSubmitResult) {
		this.txSubmitResult = txSubmitResult;
	}
}
