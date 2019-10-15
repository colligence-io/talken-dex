package io.talken.dex.api.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class TradeWalletResult {
	private boolean isActive;
	private String address;
	private Map<String, BigDecimal> balances;
}
