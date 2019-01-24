package io.colligence.talken.dex.service.integration.txTunnel;

import lombok.Data;

@Data
public class TxtServerRequest {
	private String serviceId;
	private String taskId;
	private String signatures;

	public static enum Type {
		BITCOIN("btc"),
		STELLAR("xlm"),
		ETHEREUM("eth"),
		ERC20("erc20");

		private final String param;

		Type(String param) {
			this.param = param;
		}

		public String getParam() {
			return this.param;
		}
	}
}
