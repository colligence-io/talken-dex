package io.talken.dex.api.service.integration.anchor;

import lombok.Data;

@Data
public class AncServerDeanchorRequest {
	private String taskId;
	private String uid;
	private String symbol;
	private String hash;
	private String from;
	private String to;
	private String address;
	private String memo;
	private Double value;
}