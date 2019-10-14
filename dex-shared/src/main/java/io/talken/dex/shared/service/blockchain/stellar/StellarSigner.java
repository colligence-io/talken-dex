package io.talken.dex.shared.service.blockchain.stellar;

import io.talken.dex.shared.exception.SigningException;
import org.stellar.sdk.Transaction;

public interface StellarSigner {
	void sign(Transaction tx) throws SigningException;
}