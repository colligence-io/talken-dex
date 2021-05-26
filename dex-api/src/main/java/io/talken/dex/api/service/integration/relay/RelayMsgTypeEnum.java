package io.talken.dex.api.service.integration.relay;

/**
 * The enum Relay msg type enum.
 */
@Deprecated
public enum RelayMsgTypeEnum {
    /**
     * Anchor relay msg type enum.
     */
    ANCHOR("2003"),
    /**
     * Deanchor relay msg type enum.
     */
    DEANCHOR("2004"),
    /**
     * Createoffer relay msg type enum.
     */
    CREATEOFFER("2005"),
    /**
     * Deleteoffer relay msg type enum.
     */
    DELETEOFFER("2006"),
    /**
     * Swap relay msg type enum.
     */
    SWAP("2007");

	private final String msgType;

	RelayMsgTypeEnum(String msgType) {
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
