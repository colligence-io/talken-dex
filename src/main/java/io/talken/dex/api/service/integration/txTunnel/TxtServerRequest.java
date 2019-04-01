package io.talken.dex.api.service.integration.txTunnel;

import lombok.Data;

@Data
public class TxtServerRequest {
	private String serviceId;
	private String taskId;
	private String signatures;
}
