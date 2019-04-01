package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

@Data
public class TxListRequest {
	@NotEmpty
	String sourceAccount;
	String taskId;
	String txHash;
	Long offerId;
	String sellAssetCode;
	String buyAssetCode;
}
