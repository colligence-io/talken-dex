package io.colligence.talken.dex.api.dex.offer.dto;

import io.colligence.talken.dex.api.dex.TxSubmitResult;
import lombok.Data;

@Data
public class CreateOfferSubmitResult {
	private TxSubmitResult txSubmitResult;

	public CreateOfferSubmitResult(TxSubmitResult txSubmitResult) {
		this.txSubmitResult = txSubmitResult;
	}
}
