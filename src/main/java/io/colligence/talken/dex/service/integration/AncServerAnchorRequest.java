package io.colligence.talken.dex.service.integration;

import lombok.Data;

@Data
public class AncServerAnchorRequest {
	private String taskID;
	private String uid;
	private String symbol;
	private String from;
	private String to;
	private String stellar;
	private String memo;
	private Float value;
}
