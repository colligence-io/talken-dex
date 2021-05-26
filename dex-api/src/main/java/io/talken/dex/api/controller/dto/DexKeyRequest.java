package io.talken.dex.api.controller.dto;

import lombok.Data;

/**
 * The type Dex key request.
 */
@Data
public class DexKeyRequest {
	private Long userId;
	private String taskId;
	private String transId;
	private String signature;
}
