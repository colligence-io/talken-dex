package io.colligence.talken.dex.util;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

public class AES256Util {
	private String iv;
	private Key keySpec;

	public AES256Util(String key) throws IllegalArgumentException {
		if(key.length() != 16) throw new IllegalArgumentException("key length must be 16");
		this.iv = key.substring(0, 16);
		byte[] keyBytes = new byte[16];
		byte[] b = key.getBytes(StandardCharsets.UTF_8);
		int len = b.length;
		if(len > keyBytes.length) {
			len = keyBytes.length;
		}
		System.arraycopy(b, 0, keyBytes, 0, len);

		this.keySpec = new SecretKeySpec(keyBytes, "AES");
	}

	/**
	 * AES256 으로 암호화 한다.
	 *
	 * @param str 암호화할 문자열
	 * @return
	 * @throws NoSuchAlgorithmException
	 * @throws GeneralSecurityException
	 */
	public String encrypt(String str) throws NoSuchAlgorithmException,
			GeneralSecurityException {
		Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
		c.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv.getBytes()));
		byte[] encrypted = c.doFinal(str.getBytes(StandardCharsets.UTF_8));
		return new String(Base64.encodeBase64(encrypted));
	}

	/**
	 * AES256으로 암호화된 txt 를 복호화한다.
	 *
	 * @param str 복호화할 문자열
	 * @return
	 * @throws NoSuchAlgorithmException
	 * @throws GeneralSecurityException
	 */
	public String decrypt(String str) throws NoSuchAlgorithmException,
			GeneralSecurityException {
		Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
		c.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv.getBytes()));
		byte[] byteStr = Base64.decodeBase64(str.getBytes());
		return new String(c.doFinal(byteStr), StandardCharsets.UTF_8);
	}
}
