package io.talken.dex.shared.service.tradewallet.wallet.derivation;


import io.talken.dex.shared.service.tradewallet.wallet.WalletException;

public class Ed25519DerivationException extends WalletException {

    public Ed25519DerivationException(String message) {
        super(message);
    }
}
