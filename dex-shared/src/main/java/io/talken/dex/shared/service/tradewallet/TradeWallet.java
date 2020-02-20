package io.talken.dex.shared.service.tradewallet;

import io.talken.common.util.ByteArrayUtils;
import io.talken.dex.shared.service.tradewallet.wallet.StellarWallet;
import io.talken.dex.shared.service.tradewallet.wallet.WalletException;
import org.stellar.sdk.KeyPair;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

/**
 * User trade wallet
 */
public class TradeWallet {
	/**
	 * Generate trade wallet with keybase
	 *
	 * @param keyBase
	 * @return
	 * @throws WalletException
	 * @throws NoSuchPaddingException
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidAlgorithmParameterException
	 * @throws InvalidKeyException
	 * @throws BadPaddingException
	 * @throws IllegalBlockSizeException
	 */
	public static String generate(byte[] keyBase) throws WalletException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
		if(keyBase.length % 32 != 0) throw new IllegalArgumentException("keyBase must be 32*N bytes");

		KeyPair keyPair = StellarWallet.createKeyPair(StellarWallet.generate24WordMnemonic(), null, 0);

		byte[] secret = new String(keyPair.getSecretSeed()).getBytes(StandardCharsets.UTF_8);

		for(int i = 0; i < keyBase.length / 32; i++) {
			byte[] ivBytes = new byte[16];
			byte[] keyBytes = new byte[16];

			System.arraycopy(keyBase, i * 32, ivBytes, 0, 16);
			System.arraycopy(keyBase, i * 32 + 16, keyBytes, 0, 16);

			AlgorithmParameterSpec ivSpec = new IvParameterSpec(ivBytes);
			Key keySpec = new SecretKeySpec(keyBytes, "AES");

			Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
			c.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
			secret = c.doFinal(secret);

			Arrays.fill(ivBytes, (byte) 0);
			Arrays.fill(keyBytes, (byte) 0);
		}

		String walletString = ByteArrayUtils.toHexString(secret);
		Arrays.fill(keyBase, (byte) 0);
		Arrays.fill(secret, (byte) 0);
		return walletString;
	}

	/**
	 * decrypt trade walletString with keybase
	 *
	 * @param keyBase
	 * @param walletString
	 * @return
	 * @throws NoSuchPaddingException
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidAlgorithmParameterException
	 * @throws InvalidKeyException
	 * @throws BadPaddingException
	 * @throws IllegalBlockSizeException
	 */
	public static KeyPair toKeyPair(byte[] keyBase, String walletString) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
		if(keyBase.length % 32 != 0) throw new IllegalArgumentException("keyBase must be 32*N bytes");

		byte[] secret = ByteArrayUtils.fromHexString(walletString);

		for(int i = (keyBase.length / 32) - 1; i >= 0; i--) {
			byte[] ivBytes = new byte[16];
			byte[] keyBytes = new byte[16];

			System.arraycopy(keyBase, i * 32, ivBytes, 0, 16);
			System.arraycopy(keyBase, i * 32 + 16, keyBytes, 0, 16);

			AlgorithmParameterSpec ivSpec = new IvParameterSpec(ivBytes);
			Key keySpec = new SecretKeySpec(keyBytes, "AES");

			Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
			c.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
			secret = c.doFinal(secret);

			Arrays.fill(ivBytes, (byte) 0);
			Arrays.fill(keyBytes, (byte) 0);
		}
		KeyPair kp = KeyPair.fromSecretSeed(new String(secret, StandardCharsets.UTF_8));
		Arrays.fill(keyBase, (byte) 0);
		Arrays.fill(secret, (byte) 0);
		return kp;
	}
}
