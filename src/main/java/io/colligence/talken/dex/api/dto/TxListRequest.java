package io.colligence.talken.dex.api.dto;

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
