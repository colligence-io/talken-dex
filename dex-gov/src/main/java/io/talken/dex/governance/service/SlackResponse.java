package io.talken.dex.governance.service;

import io.talken.common.util.integration.RestApiPlainTextResponseInterface;

public class SlackResponse implements RestApiPlainTextResponseInterface {
	private String message;

	@Override
	public boolean isSuccess() {
		return true;
	}

	@Override
	public String getCode() {
		return "";
	}

	@Override
	public String getMessage() {
		return this.message;
	}

	@Override
	public void setMessage(String message) {
		this.message = message;
	}
}
