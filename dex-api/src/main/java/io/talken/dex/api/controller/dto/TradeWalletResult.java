package io.talken.dex.api.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.talken.dex.shared.service.tradewallet.TradeWalletStatus;
import lombok.Data;
import org.stellar.sdk.responses.AccountResponse;

@Data
public class TradeWalletResult {
	private TradeWalletStatus status;
	private String accountId;
	@JsonIgnore
	private AccountResponse accountResponse;
}
