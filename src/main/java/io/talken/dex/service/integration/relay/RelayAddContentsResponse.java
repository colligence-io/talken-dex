package io.talken.dex.service.integration.relay;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RelayAddContentsResponse {
	private String transId;
	private String status;
	private LocalDateTime regDt;
	private LocalDateTime endDt;
}
