package io.talken.dex.shared.service.blockchain.stellar;

import io.talken.common.util.collection.SingleKeyObject;
import io.talken.dex.shared.exception.SigningException;
import org.stellar.sdk.Transaction;

public interface StellarSigner extends SingleKeyObject<String> {
	void sign(Transaction tx) throws SigningException;
}