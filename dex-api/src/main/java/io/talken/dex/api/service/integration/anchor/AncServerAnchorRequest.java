package io.talken.dex.api.service.integration.anchor;

import lombok.Data;

@Data
public class AncServerAnchorRequest {
	private String taskId;
	private String uid;
	private String symbol;
	private String from;
	private String to;
	private String stellar;
	private String memo;
	private Float value;
}
