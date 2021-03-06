package io.talken.dex.shared.service.tradewallet.wallet;
import io.talken.dex.shared.service.tradewallet.wallet.derivation.Ed25519Derivation;
import io.talken.dex.shared.service.tradewallet.wallet.mnemonic.Mnemonic;
import io.talken.dex.shared.service.tradewallet.wallet.mnemonic.Strength;
import io.talken.dex.shared.service.tradewallet.wallet.mnemonic.WordList;
import org.stellar.sdk.KeyPair;

import javax.annotation.Nullable;

/**
 * Generates Mnemonic with corresponding Stellar Keypair.
 * Created by cristi.paval on 3/14/18.
 */
public class StellarWallet {

    /**
     * Generate 12 word mnemonic char [ ].
     *
     * @return the char [ ]
     * @throws WalletException the wallet exception
     */
    public static char[] generate12WordMnemonic() throws WalletException {
        return Mnemonic.create(Strength.NORMAL, WordList.ENGLISH);
    }

    /**
     * Generate 24 word mnemonic char [ ].
     *
     * @return the char [ ]
     * @throws WalletException the wallet exception
     */
    public static char[] generate24WordMnemonic() throws WalletException {
        return Mnemonic.create(Strength.HIGH, WordList.ENGLISH);
    }

    /**
     * Create key pair key pair.
     *
     * @param mnemonic   the mnemonic
     * @param passphrase the passphrase
     * @param index      the index
     * @return the key pair
     * @throws WalletException the wallet exception
     */
    public static KeyPair createKeyPair(char[] mnemonic, @Nullable char[] passphrase, int index) throws WalletException {
        byte[] bip39Seed = Mnemonic.createSeed(mnemonic, passphrase);

        Ed25519Derivation masterPrivateKey = Ed25519Derivation.fromSecretSeed(bip39Seed);
        Ed25519Derivation purpose = masterPrivateKey.derived(44);
        Ed25519Derivation coinType = purpose.derived(148);
        Ed25519Derivation account = coinType.derived(index);
        return KeyPair.fromSecretSeed(account.getPrivateKey());
    }
}
