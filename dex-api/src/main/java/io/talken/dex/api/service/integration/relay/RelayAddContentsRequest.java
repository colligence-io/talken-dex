package io.talken.dex.api.service.integration.relay;

import lombok.Data;

/**
 * The type Relay add contents request.
 */
@Deprecated
@Data
public class RelayAddContentsRequest {
	private String msgType;
	private String userId;
	private String pushBody;
	private String pushImage;
	private String pushTitle;
	private String msgContents;
}
