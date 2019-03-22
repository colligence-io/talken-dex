package io.colligence.talken.dex.service.integration.relay;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.colligence.talken.common.util.AES256Util;
import io.colligence.talken.common.util.JSONWriter;
import io.colligence.talken.common.util.RandomStringGenerator;

import javax.crypto.KeyGenerator;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

public class RelayEncryptedContent<T> {
	private T data;
	private String key;
	private String encrypted;

	public RelayEncryptedContent(T data) throws JsonProcessingException, GeneralSecurityException {
		this.data = data;
		encrypt();
	}

	private void encrypt() throws JsonProcessingException, GeneralSecurityException {
		try {
			KeyGenerator keyGen = KeyGenerator.getInstance("AES");
			keyGen.init(256);
			key = ByteArrayUtil.toHexString(keyGen.generateKey().getEncoded()).substring(0, 16);
		} catch(NoSuchAlgorithmException e) {
			key = RandomStringGenerator.generate(16);
		}

		encrypted = new AES256Util(key).encrypt(JSONWriter.toJsonString(data));
	}

	public T getData() {
		return data;
	}

	public String getKey() {
		return key;
	}

	public String getEncrypted() {
		return encrypted;
	}
}
