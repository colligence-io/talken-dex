package io.talken.dex.api.controller.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

@Data
public class TxListRequest {
	@NotEmpty
	private String sourceAccount;
	private String taskId;
	private String txHash;
	private Long offerId;
	private String sellAssetCode;
	private String buyAssetCode;
}
