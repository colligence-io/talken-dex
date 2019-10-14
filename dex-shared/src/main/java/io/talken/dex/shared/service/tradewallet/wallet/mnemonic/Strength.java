package io.talken.dex.shared.service.tradewallet.wallet.mnemonic;

/**
 * Enum class defining the strength of the mnemonic.
 * Created by cristi.paval on 3/13/18.
 */

public enum Strength {
    NORMAL(128), HIGH(256);

    private int rawValue;

    Strength(int rawValue) {
        this.rawValue = rawValue;
    }

    public int getRawValue() {
        return rawValue;
    }
}
