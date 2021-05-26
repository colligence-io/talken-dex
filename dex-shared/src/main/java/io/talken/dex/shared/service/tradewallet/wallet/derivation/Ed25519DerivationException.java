package io.talken.dex.shared.service.tradewallet.wallet.derivation;


import io.talken.dex.shared.service.tradewallet.wallet.WalletException;

/**
 * The type Ed 25519 derivation exception.
 */
public class Ed25519DerivationException extends WalletException {

    /**
     * Instantiates a new Ed 25519 derivation exception.
     *
     * @param message the message
     */
    public Ed25519DerivationException(String message) {
        super(message);
    }
}
