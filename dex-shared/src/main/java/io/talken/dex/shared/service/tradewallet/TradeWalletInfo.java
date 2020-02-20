package io.talken.dex.shared.service.tradewallet;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.stellar.sdk.Asset;
import org.stellar.sdk.responses.AccountResponse;

import java.math.BigDecimal;

@Data
public final class TradeWalletInfo {
	private boolean isConfirmed;

	@JsonIgnore
	@Getter(AccessLevel.PUBLIC)
	@Setter(AccessLevel.NONE)
	private User user;

	@Getter(AccessLevel.PUBLIC)
	@Setter(AccessLevel.PACKAGE)
	private String accountId;

	/**
	 * should not be exported in json
	 */
	@JsonIgnore
	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private String secret;

	@JsonIgnore
	private AccountResponse accountResponse = null;

	public TradeWalletInfo(User user) {
		this.user = user;
	}

	public String getUid() {
		return user.getUid();
	}

	public BigDecimal getBalance(Asset asset) {
		return StellarConverter.getAccountBalance(accountResponse, asset);
	}

	public BigDecimal getNativeBalance() {
		return StellarConverter.getAccountNativeBalance(accountResponse);
	}

	public boolean isTrusted(Asset asset) {
		return StellarConverter.isAccountTrusted(accountResponse, asset);
	}

	public boolean hasEnough(Asset asset, BigDecimal amount) {
		return StellarConverter.isAccountBalanceEnough(accountResponse, asset, amount);
	}
}
