package io.talken.dex.shared.service.tradewallet;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.stellar.sdk.responses.AccountResponse;

@Data
public final class TradeWalletInfo {
	private boolean isConfirmed;

	@Getter(AccessLevel.PUBLIC)
	@Setter(AccessLevel.NONE)
	private String uid;

	@Getter(AccessLevel.PUBLIC)
	@Setter(AccessLevel.PACKAGE)
	private String accountId;

	@JsonIgnore
	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private String secret;

	@JsonIgnore
	private AccountResponse accountResponse;

	public TradeWalletInfo(String uid) {
		this.uid = uid;
	}
}
