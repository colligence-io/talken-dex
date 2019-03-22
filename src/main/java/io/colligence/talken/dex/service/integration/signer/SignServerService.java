package io.colligence.talken.dex.service.integration.signer;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpStatusCodes;
import io.colligence.talken.common.persistence.vault.VaultSecretReader;
import io.colligence.talken.common.util.ByteArrayUtils;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexSettings;
import io.colligence.talken.dex.exception.SigningException;
import io.colligence.talken.dex.service.integration.APIResult;
import io.colligence.talken.dex.service.integration.AbstractRestApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.xdr.DecoratedSignature;
import org.stellar.sdk.xdr.PublicKey;
import org.stellar.sdk.xdr.SignatureHint;
import org.stellar.sdk.xdr.XdrDataOutputStream;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;

@Service
@Scope("singleton")
public class SignServerService extends AbstractRestApiService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(SignServerService.class);

	@Autowired
	private DexSettings dexSettings;

	@Autowired
	private VaultSecretReader vaultSecretReader;

	private static String signingUrl;
	private static String introduceUrl;
	private static String answerUrl;

	private static String token;

	private static SignServerKeyMap keymap = new SignServerKeyMap();

	private static class SignServerKeyMap extends HashMap<String, String> {
		private static final long serialVersionUID = -6982099027984561756L;
	}

	@PostConstruct
	private void init() {
		signingUrl = dexSettings.getSignServer().getAddr() + "/sign";
		introduceUrl = dexSettings.getSignServer().getAddr() + "/introduce";
		answerUrl = dexSettings.getSignServer().getAddr() + "/answer";

		updateAccessToken();
	}

	private void updateAccessToken() {
		try {
			String privateKey = dexSettings.getSignServer().getAppKey();
			SignServerIntroduceRequest request = new SignServerIntroduceRequest();
			request.setMyNameIs(dexSettings.getSignServer().getAppName());

			APIResult<SignServerIntroduceResponse> introResult = requestPost(introduceUrl, request, SignServerIntroduceResponse.class);

			if(!introResult.isSuccess()) {
				logger.error("Cannot get signServer Token : {}, {}, {}", introResult.getResponseCode(), introResult.getErrorCode(), introResult.getErrorMessage());
				return;
			}

			byte[] question = Base64.getDecoder().decode(introResult.getData().getData().getQuestion());

			KeyPair keyPair = KeyPair.fromSecretSeed(privateKey);

			byte[] signature = keyPair.sign(question);

			SignServerAnswerRequest request2 = new SignServerAnswerRequest();
			request2.setMyNameIs(dexSettings.getSignServer().getAppName());
			request2.setMyAnswerIs(Base64.getEncoder().encodeToString(signature));

			APIResult<SignServerAnswerResponse> answerResult = requestPost(answerUrl, request2, SignServerAnswerResponse.class);

			if(!answerResult.isSuccess()) {
				logger.error("Cannot get signServer Token : {}, {}, {}", answerResult.getResponseCode(), answerResult.getErrorCode(), answerResult.getErrorMessage());
				return;
			}

			logger.info("Received new signServer JWT Token");
			token = answerResult.getData().getData().getWelcomePresent();
		} catch(Exception e) {
			logger.exception(e);
		}
	}

	private APIResult<SignServerSignResponse> requestSign(SignServerSignRequest request) throws SigningException {
		if(token == null) updateAccessToken();

		if(token == null) {
			throw new SigningException(request.getAddress(), "Cannot request sign, access token is null");
		}

		HttpHeaders headers = new HttpHeaders();
		headers.setAuthorization("Bearer " + token);
		APIResult<SignServerSignResponse> result = requestPost(signingUrl, headers, request, SignServerSignResponse.class);

		if(result.isSuccess()) return result;

		// if failed code is not "UNAUTHORIZED", just return error result
		if(result.getResponseCode() != HttpStatusCodes.STATUS_CODE_UNAUTHORIZED) {
			return result;
		}

		// case of unauthorized result, mostly case of token expiration
		// try update access token and send request again
		updateAccessToken();
		headers.setAuthorization("Bearer " + token);
		return requestPost(signingUrl, headers, request, SignServerSignResponse.class);
	}

	private String getAddressKeyID(String address) {
		if(!keymap.containsKey("XLM:" + address))
			keymap = vaultSecretReader.readSecret("signserver-keymap", SignServerKeyMap.class);
		return keymap.get("XLM:" + address);
	}

	public void signTransaction(Transaction tx) throws SigningException {
		String accountId = tx.getSourceAccount().getAccountId();

		String keyId = getAddressKeyID(accountId);
		if(keyId == null) {
			throw new SigningException(accountId, "Cannot find ssk");
		}

		SignServerSignRequest ssReq = new SignServerSignRequest();
		ssReq.setKeyID(keyId);
		ssReq.setAddress(accountId);
		ssReq.setType("XLM");
		ssReq.setData(ByteArrayUtils.toHexString(tx.hash()));

		APIResult<SignServerSignResponse> signResult = requestSign(ssReq);

		if(!signResult.isSuccess()) {
			throw new SigningException(accountId, signResult.getErrorCode() + " : " + signResult.getErrorMessage());
		}

		byte[] sigBytes = ByteArrayUtils.fromHexString(signResult.getData().getData().getSignature());

		org.stellar.sdk.xdr.Signature signature = new org.stellar.sdk.xdr.Signature();
		signature.setSignature(sigBytes);

		SignatureHint signatureHint = new SignatureHint();
		try {
			ByteArrayOutputStream publicKeyBytesStream = new ByteArrayOutputStream();
			XdrDataOutputStream xdrOutputStream = new XdrDataOutputStream(publicKeyBytesStream);
			PublicKey.encode(xdrOutputStream, tx.getSourceAccount().getXdrPublicKey());
			byte[] publicKeyBytes = publicKeyBytesStream.toByteArray();
			byte[] signatureHintBytes = Arrays.copyOfRange(publicKeyBytes, publicKeyBytes.length - 4, publicKeyBytes.length);
			signatureHint.setSignatureHint(signatureHintBytes);
		} catch(IOException e) {
			throw new SigningException(e, accountId, e.getMessage());
		}

		DecoratedSignature decoratedSignature = new DecoratedSignature();
		decoratedSignature.setHint(signatureHint);
		decoratedSignature.setSignature(signature);

		tx.getSignatures().add(decoratedSignature);
	}
}
