package io.talken.dex.api.service.integration;

public enum PrivateWalletMsgTypeEnum {
	WITHDRAW("3000"),
	ANCHOR("3001");

	private final String msgType;

	PrivateWalletMsgTypeEnum(String msgType) {
		this.msgType = msgType;
	}

	public String getMsgType() {
		return msgType;
	}
}
