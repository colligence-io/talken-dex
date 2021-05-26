package io.talken.dex.api.service.integration;

/**
 * The enum Private wallet msg type enum.
 */
public enum PrivateWalletMsgTypeEnum {
    /**
     * Transfer private wallet msg type enum.
     */
    TRANSFER("3000"),
    /**
     * Anchor private wallet msg type enum.
     */
    ANCHOR("3001");

	private final String msgType;

	PrivateWalletMsgTypeEnum(String msgType) {
		this.msgType = msgType;
	}

    /**
     * Gets msg type.
     *
     * @return the msg type
     */
    public String getMsgType() {
		return msgType;
	}
}
