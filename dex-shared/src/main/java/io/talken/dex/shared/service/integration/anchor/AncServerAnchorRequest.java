package io.talken.dex.shared.service.integration.anchor;

import lombok.Data;

import java.math.BigDecimal;

@Deprecated
@Data
public class AncServerAnchorRequest {
	private String taskId;
	private String uid;
	private String symbol;
	private String from;
	private String to;
	private String stellar;
	private String memo;
	private BigDecimal value;
}
