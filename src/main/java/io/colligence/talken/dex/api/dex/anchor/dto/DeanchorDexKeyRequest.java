package io.colligence.talken.dex.api.dex.anchor.dto;

import lombok.Data;

@Data
public class DeanchorDexKeyRequest {
	private String taskId;
	private String transId;
	private String signature;
}
