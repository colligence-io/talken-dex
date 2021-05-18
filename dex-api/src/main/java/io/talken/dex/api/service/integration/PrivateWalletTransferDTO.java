package io.talken.dex.api.service.integration;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * The type Private wallet transfer dto.
 */
@Data
public class PrivateWalletTransferDTO {
	private String msgType;
	private BlockChainPlatformEnum platform;
	private BlockChainPlatformEnum.WalletType walletType;
	private BlockChainPlatformEnum.SignType signType;
	private String symbol;
	private Map<String, Object> aux = new HashMap<>();
	private String addrFrom;
	private String addrTo;
	private String addrTrade;
	private BigDecimal amount;
	private BigDecimal netfee;
	private String memo;

    /**
     * Instantiates a new Private wallet transfer dto.
     *
     * @param msgType the msg type
     */
    public PrivateWalletTransferDTO(PrivateWalletMsgTypeEnum msgType) {
		this.msgType = msgType.getMsgType();
	}
}
