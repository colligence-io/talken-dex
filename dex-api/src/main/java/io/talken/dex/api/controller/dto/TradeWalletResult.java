package io.talken.dex.api.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class TradeWalletResult {
	private boolean isActive;
	private String address;
	private Map<String, TradeWalletResult.Balance> balances;

	@Data
	public static class Balance {
		private BigDecimal balance;
		private BigDecimal sellLiability;
		private BigDecimal buyLiability;
	}
}
