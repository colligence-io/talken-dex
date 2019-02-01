package io.colligence.talken.dex.api.dex;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.colligence.talken.common.util.JSONWriter;
import io.colligence.talken.common.util.RandomStringGenerator;
import io.colligence.talken.dex.util.AES256Util;

import javax.crypto.KeyGenerator;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

public class TxEncryptedData {
	private TxInformation txInfo;
	private String key;
	private String encrypted;

	public TxEncryptedData(TxInformation txInfo) throws JsonProcessingException, GeneralSecurityException {
		this.txInfo = txInfo;
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

		encrypted = new AES256Util(key).encrypt(JSONWriter.toJsonString(txInfo));
	}

	public TxInformation getTxInfo() {
		return txInfo;
	}

	public String getKey() {
		return key;
	}

	public String getEncrypted() {
		return encrypted;
	}
}
