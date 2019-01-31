package io.colligence.talken.dex.util;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.colligence.talken.dex.exception.SignatureVerificationFailedException;
import org.stellar.sdk.KeyPair;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class StellarSignVerifier {
	public static boolean verifySignBase64(String accountId, String orgData, String base64Signature) throws SignatureVerificationFailedException {
		try {
			if(KeyPair.fromAccountId(accountId).verify(orgData.getBytes(StandardCharsets.UTF_8), Base64.getDecoder().decode(base64Signature))) {
				return true;
			} else {
				return false;
			}
		} catch(Exception e) {
			throw new SignatureVerificationFailedException(e, orgData, base64Signature);
		}
	}

	public static boolean verifySignHex(String accountId, String orgData, String hexSignature) throws SignatureVerificationFailedException {
		try {
			if(KeyPair.fromAccountId(accountId).verify(orgData.getBytes(StandardCharsets.UTF_8), ByteArrayUtil.hexStringToByteArray(hexSignature)))
				return true;
			else
				return false;
		} catch(Exception e) {
			throw new SignatureVerificationFailedException(e, orgData, hexSignature);
		}
	}
}
