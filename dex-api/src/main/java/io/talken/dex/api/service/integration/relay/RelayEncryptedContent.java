package io.talken.dex.api.service.integration.relay;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.talken.common.util.AES256Util;
import io.talken.common.util.GSONWriter;
import io.talken.common.util.RandomStringGenerator;

import javax.crypto.KeyGenerator;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * The type Relay encrypted content.
 *
 * @param <T> the type parameter
 */
@Deprecated
public class RelayEncryptedContent<T> {
	private T data;
	private String key;
	private String encrypted;
	private Map<String, String> description;

    /**
     * Instantiates a new Relay encrypted content.
     *
     * @param data the data
     * @throws GeneralSecurityException the general security exception
     */
    public RelayEncryptedContent(T data) throws GeneralSecurityException {
		this.data = data;
		this.description = new HashMap<>();
		encrypt();
	}

	private void encrypt() throws GeneralSecurityException {
		try {
			KeyGenerator keyGen = KeyGenerator.getInstance("AES");
			keyGen.init(256);
			key = ByteArrayUtil.toHexString(keyGen.generateKey().getEncoded()).substring(0, 16);
		} catch(NoSuchAlgorithmException e) {
			key = RandomStringGenerator.generate(16);
		}

		encrypted = new AES256Util(key).encrypt(GSONWriter.toJsonString(data));
	}

    /**
     * Gets data.
     *
     * @return the data
     */
    public T getData() {
		return data;
	}

    /**
     * Add description.
     *
     * @param key   the key
     * @param value the value
     */
    public void addDescription(String key, String value) {
		this.description.put(key, value);
	}

    /**
     * Gets key.
     *
     * @return the key
     */
    public String getKey() {
		return key;
	}

    /**
     * Gets encrypted.
     *
     * @return the encrypted
     */
    public String getEncrypted() {
		return encrypted;
	}

    /**
     * Gets description.
     *
     * @return the description
     */
    public Map<String, String> getDescription() {
		return this.description;
	}
}
