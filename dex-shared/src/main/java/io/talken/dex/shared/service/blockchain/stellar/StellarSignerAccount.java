package io.talken.dex.shared.service.blockchain.stellar;

import io.talken.dex.shared.exception.SigningException;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.Transaction;

/**
 * Stellar signer for KeyPair
 */
public class StellarSignerAccount implements StellarSigner {
	private KeyPair kp;

	public StellarSignerAccount(KeyPair kp) {
		this.kp = kp;
	}

	@Override
	public String __getSKey__() {
		return "KP:" + kp.getAccountId();
	}

	@Override
	public void sign(Transaction tx) throws SigningException {
		if(kp == null) throw new SigningException("Null", "Key is null");
		if(tx == null) throw new SigningException(kp.getAccountId(), "Tx is null");
		if(!kp.canSign()) throw new SigningException(kp.getAccountId(), "Not a signable account");
		tx.sign(kp);
	}
}