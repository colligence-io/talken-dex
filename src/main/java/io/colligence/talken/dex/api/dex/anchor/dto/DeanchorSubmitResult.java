package io.colligence.talken.dex.api.dex.anchor.dto;

import io.colligence.talken.dex.api.dex.TxSubmitResult;
import lombok.Data;

@Data
public class DeanchorSubmitResult {
	private TxSubmitResult txSubmitResult;

	public DeanchorSubmitResult(TxSubmitResult txSubmitResult) {
		this.txSubmitResult = txSubmitResult;
	}
}
