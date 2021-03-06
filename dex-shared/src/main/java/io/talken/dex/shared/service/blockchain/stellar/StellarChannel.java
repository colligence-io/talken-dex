package io.talken.dex.shared.service.blockchain.stellar;

import lombok.Data;
import org.stellar.sdk.AssetTypeNative;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.responses.AccountResponse;

import java.math.BigDecimal;

/**
 * Stellar transaction channel
 * see https://www.stellar.org/developers/guides/channels.html
 */
@Data
public class StellarChannel implements Comparable<StellarChannel> {
	private KeyPair keyPair;
	private BigDecimal balance;

    /**
     * Instantiates a new Stellar channel.
     *
     * @param kp the kp
     */
    public StellarChannel(KeyPair kp) {
		this.keyPair = kp;
		this.balance = BigDecimal.ZERO;
	}

    /**
     * Gets account id.
     *
     * @return the account id
     */
    public String getAccountId() {return getKeyPair().getAccountId();}

	@Override
	public int compareTo(StellarChannel other) {
		return balance.compareTo(other.balance) * -1;
	}

    /**
     * Update.
     *
     * @param ar the ar
     */
    public void update(AccountResponse ar) {
		for(AccountResponse.Balance balance : ar.getBalances()) {
			if(balance.getAsset() instanceof AssetTypeNative) {
				this.balance = new BigDecimal(balance.getBalance());
				break;
			}
		}
	}
}
