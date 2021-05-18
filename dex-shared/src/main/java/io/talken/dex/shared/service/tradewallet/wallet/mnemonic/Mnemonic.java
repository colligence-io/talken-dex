package io.talken.dex.shared.service.tradewallet.wallet.mnemonic;


import io.talken.dex.shared.service.tradewallet.wallet.util.PrimitiveUtil;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.List;

/**
 * Mnemonic.
 * Created by cristi.paval on 3/13/18.
 */
public class Mnemonic {

    /**
     * Create char [ ].
     *
     * @param strength the strength
     * @param wordList the word list
     * @return the char [ ]
     * @throws MnemonicException the mnemonic exception
     */
    public static char[] create(Strength strength, WordList wordList) throws MnemonicException {
        int byteCount = strength.getRawValue() / 8;
        byte[] bytes = SecureRandom.getSeed(byteCount);
        return create(bytes, wordList);
    }

    private static char[] create(byte[] entropy, WordList wordList) throws MnemonicException {

        byte[] hashBits;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            hashBits = digest.digest(entropy);
        } catch (Exception e) {
            throw new MnemonicException("Fatal error! SHA-256 algorithm does not exist!");
        }


        char[] binaryHash = PrimitiveUtil.bytesToBinaryAsChars(hashBits);
        char[] checkSum = PrimitiveUtil.charSubArray(binaryHash, 0, entropy.length * 8 / 32);
        char[] entropyBits = PrimitiveUtil.bytesToBinaryAsChars(entropy);
        StringBuilder concatenatedBits = new StringBuilder().append(entropyBits).append(checkSum);

        List<char[]> words = wordList.getWordsAsCharArray();
        StringBuilder mnemonicBuilder = new StringBuilder();
        for (int index = 0; index < concatenatedBits.length() / 11; ++index) {

            int startIndex = index * 11;
            int endIndex = startIndex + 11;
            char[] wordIndexAsChars = new char[endIndex - startIndex];
            concatenatedBits.getChars(startIndex, endIndex, wordIndexAsChars, 0);
            int wordIndex = PrimitiveUtil.binaryCharsToInt(wordIndexAsChars);
            mnemonicBuilder.append(words.get(wordIndex)).append(' ');
        }

        char[] mnemonic = new char[mnemonicBuilder.length() - 1];
        mnemonicBuilder.getChars(0, mnemonicBuilder.length() - 1, mnemonic, 0);
        return mnemonic;
    }

    /**
     * Create seed byte [ ].
     *
     * @param mnemonic   the mnemonic
     * @param passphrase the passphrase
     * @return the byte [ ]
     * @throws MnemonicException the mnemonic exception
     */
    public static byte[] createSeed(char[] mnemonic, char[] passphrase) throws MnemonicException {

        char[] saltChars = new char[]{'m', 'n', 'e', 'm', 'o', 'n', 'i', 'c'};
        if (passphrase != null) {
            saltChars = PrimitiveUtil.concatCharArrays(saltChars, passphrase);
        }
        byte[] salt = PrimitiveUtil.toBytes(saltChars);

        try {
            KeySpec ks = new PBEKeySpec(mnemonic, salt, 2048, 512);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
            return skf.generateSecret(ks).getEncoded();
        } catch (Exception e) {
            throw new MnemonicException("Fatal error when generating seed from mnemonic!");
        }
    }
}
