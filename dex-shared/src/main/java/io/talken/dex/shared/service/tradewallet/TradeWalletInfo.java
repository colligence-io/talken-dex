package io.talken.dex.shared.service.tradewallet;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.stellar.sdk.Asset;
import org.stellar.sdk.AssetTypeNative;
import org.stellar.sdk.responses.AccountResponse;

import java.math.BigDecimal;

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
	private AccountResponse accountResponse = null;

	public TradeWalletInfo(String uid) {
		this.uid = uid;
	}

	public BigDecimal getBalance(Asset asset) {
		if(accountResponse == null) return null;
		for(AccountResponse.Balance _bal : accountResponse.getBalances()) {
			if(_bal.getAsset().equals(asset)) {
				return new BigDecimal(_bal.getBalance());
			}
		}
		return null;
	}

	public BigDecimal getNativeBalance() {
		return getBalance(new AssetTypeNative());
	}

	public boolean isTrusted(Asset asset) {
		if(accountResponse == null) return false;
		if(getBalance(asset) == null) return false;
		else return true;
	}

	public boolean hasEnough(Asset asset, BigDecimal amount) {
		final BigDecimal b = getBalance(asset);
		if(b == null) return false;
		else return b.compareTo(amount) >= 0;
	}
}
