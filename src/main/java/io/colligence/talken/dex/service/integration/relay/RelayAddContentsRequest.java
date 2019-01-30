package io.colligence.talken.dex.service.integration.relay;

import lombok.Data;

@Data
public class RelayAddContentsRequest {
	private String msgType;
	private String userId;
	private String pushBody;
	private String pushImage;
	private String pushTitle;
	private String msgContents;
}
