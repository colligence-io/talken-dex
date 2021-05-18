package io.talken.dex.api.service.integration.relay.dto;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * The type Relay transfer dto.
 */
@Deprecated
@Data
public class RelayTransferDTO {
	private BlockChainPlatformEnum platform;
	private BlockChainPlatformEnum.WalletType walletType;
	private BlockChainPlatformEnum.SignType signType;
	private String symbol;
	private Map<String, Object> aux = new HashMap<>();
	private String from;
	private String to;
	private BigDecimal amount;
	private BigDecimal netfee;
	private String memo;
}
