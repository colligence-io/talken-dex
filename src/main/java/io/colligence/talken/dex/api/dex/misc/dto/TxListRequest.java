package io.colligence.talken.dex.api.dex.misc.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

@Data
public class TxListRequest {
	@NotEmpty
	String sourceAccount;
	String taskId;
	String txHash;
	Long offerId;
}
