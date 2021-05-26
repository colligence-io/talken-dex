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

/**
 * The type Trade wallet info.
 */
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

    /**
     * Instantiates a new Trade wallet info.
     *
     * @param user the user
     */
    public TradeWalletInfo(User user) {
		this.user = user;
	}

    /**
     * Gets uid.
     *
     * @return the uid
     */
    public String getUid() {
		return user.getUid();
	}

    /**
     * Gets balance.
     *
     * @param asset the asset
     * @return the balance
     */
    public BigDecimal getBalance(Asset asset) {
		return StellarConverter.getAccountBalance(accountResponse, asset);
	}

    /**
     * Gets native balance.
     *
     * @return the native balance
     */
    public BigDecimal getNativeBalance() {
		return StellarConverter.getAccountNativeBalance(accountResponse);
	}

    /**
     * Is trusted boolean.
     *
     * @param asset the asset
     * @return the boolean
     */
    public boolean isTrusted(Asset asset) {
		return StellarConverter.isAccountTrusted(accountResponse, asset);
	}

    /**
     * Has enough boolean.
     *
     * @param asset  the asset
     * @param amount the amount
     * @return the boolean
     */
    public boolean hasEnough(Asset asset, BigDecimal amount) {
		return StellarConverter.isAccountBalanceEnough(accountResponse, asset, amount);
	}
}
