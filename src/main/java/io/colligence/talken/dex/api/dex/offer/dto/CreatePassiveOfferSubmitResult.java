package io.colligence.talken.dex.api.dex.offer.dto;

import io.colligence.talken.dex.api.dex.TxSubmitResult;
import lombok.Data;

@Data
public class CreatePassiveOfferSubmitResult {
	private TxSubmitResult txSubmitResult;

	public CreatePassiveOfferSubmitResult(TxSubmitResult txSubmitResult) {
		this.txSubmitResult = txSubmitResult;
	}
}
