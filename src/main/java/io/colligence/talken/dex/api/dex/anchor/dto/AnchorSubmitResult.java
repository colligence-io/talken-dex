package io.colligence.talken.dex.api.dex.anchor.dto;

import io.colligence.talken.dex.api.dex.TxSubmitResult;
import lombok.Data;

@Data
public class AnchorSubmitResult {
	private TxSubmitResult txSubmitResult;

	public AnchorSubmitResult(TxSubmitResult txSubmitResult) {
		this.txSubmitResult = txSubmitResult;
	}
}
