package io.talken.dex.api.service.integration.signer;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpStatusCodes;
import io.talken.common.util.ByteArrayUtils;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.DexSettings;
import io.talken.dex.api.exception.SigningException;
import io.talken.dex.api.service.integration.APIResult;
import io.talken.dex.api.service.integration.AbstractRestApiService;
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
import java.util.Map;

@Service
@Scope("singleton")
public class SignServerService extends AbstractRestApiService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(SignServerService.class);

	@Autowired
	private DexSettings dexSettings;

	private static String signingUrl;
	private static String introduceUrl;
	private static String answerUrl;

	private static String token;

	private static Map<String, String> answers = new HashMap<>();

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

			String question = introResult.getData().getData().getQuestion();

			byte[] qBytes = Base64.getDecoder().decode(question);

			KeyPair keyPair = KeyPair.fromSecretSeed(privateKey);

			byte[] sBytes = keyPair.sign(qBytes);

			SignServerAnswerRequest request2 = new SignServerAnswerRequest();
			request2.setMyNameIs(dexSettings.getSignServer().getAppName());
			request2.setYourQuestionWas(question);
			request2.setMyAnswerIs(Base64.getEncoder().encodeToString(sBytes));

			APIResult<SignServerAnswerResponse> answerResult = requestPost(answerUrl, request2, SignServerAnswerResponse.class);

			if(!answerResult.isSuccess()) {
				logger.error("Cannot get signServer Token : {}, {}, {}", answerResult.getResponseCode(), answerResult.getErrorCode(), answerResult.getErrorMessage());
				return;
			}

			Map<String, String> newAnswers = new HashMap<>();
			for(Map.Entry<String, String> _kv : answerResult.getData().getData().getWelcomePackage().entrySet()) {
				byte[] kquestion = Base64.getDecoder().decode(_kv.getValue());
				byte[] kanswer = keyPair.sign(kquestion);

				newAnswers.put(_kv.getKey(), Base64.getEncoder().encodeToString(kanswer));
			}

			logger.info("Received new signServer JWT Token");

			token = answerResult.getData().getData().getWelcomePresent();
			answers = newAnswers;

		} catch(Exception e) {
			token = null;
			answers = new HashMap<>();
			logger.exception(e);
		}
	}

	private APIResult<SignServerSignResponse> requestSign(String accountId, byte[] message) throws SigningException {
		if(token == null) updateAccessToken();

		if(token == null) {
			throw new SigningException(accountId, "Cannot request sign, access token is null");
		}

		if(!answers.containsKey("XLM:" + accountId))
			throw new SigningException(accountId, "Cannot find ssk");

		SignServerSignRequest request = new SignServerSignRequest();
		request.setAddress(accountId);
		request.setType("XLM");
		request.setData(ByteArrayUtils.toHexString(message));
		request.setAnswer(answers.get("XLM:" + accountId));

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
		logger.debug("ss token expired, getting new one");
		updateAccessToken();
		headers.setAuthorization("Bearer " + token);
		return requestPost(signingUrl, headers, request, SignServerSignResponse.class);
	}

	public void signTransaction(Transaction tx) throws SigningException {
		String accountId = tx.getSourceAccount().getAccountId();

		APIResult<SignServerSignResponse> signResult = requestSign(accountId, tx.hash());

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
