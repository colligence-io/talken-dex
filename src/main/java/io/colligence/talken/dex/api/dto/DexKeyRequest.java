package io.colligence.talken.dex.api.dto;

import lombok.Data;

@Data
public class DexKeyRequest {
	private Long userId;
	private String taskId;
	private String transId;
	private String signature;
}
