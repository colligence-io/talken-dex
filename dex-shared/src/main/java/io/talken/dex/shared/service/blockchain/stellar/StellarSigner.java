package io.talken.dex.shared.service.blockchain.stellar;

import io.talken.common.util.collection.SingleKeyObject;
import io.talken.dex.shared.exception.SigningException;
import org.stellar.sdk.Transaction;

/**
 * Stellar Signer interface
 * this interface give abstract way for signing various type of account (KeyPair, TSS...)
 */
public interface StellarSigner extends SingleKeyObject<String> {
    /**
     * Sign.
     *
     * @param tx the tx
     * @throws SigningException the signing exception
     */
    void sign(Transaction tx) throws SigningException;
}