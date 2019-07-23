package io.talken.dex.governance.service.integration.signer;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpStatusCodes;
import io.talken.common.util.ByteArrayUtils;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.RestApiResult;
import io.talken.common.util.integration.AbstractRestApiService;
import io.talken.dex.governance.GovSettings;
import io.talken.dex.shared.exception.SigningException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.xdr.DecoratedSignature;
import org.stellar.sdk.xdr.PublicKey;
import org.stellar.sdk.xdr.SignatureHint;
import org.stellar.sdk.xdr.XdrDataOutputStream;
import org.web3j.crypto.*;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;
import org.web3j.utils.Bytes;
import org.web3j.utils.Numeric;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@Service
@Scope("singleton")
public class SignServerService extends AbstractRestApiService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(SignServerService.class);

	@Autowired
	private GovSettings govSettings;

	private static String signingUrl;
	private static String introduceUrl;
	private static String answerUrl;

	private static String token;

	private static Map<String, String> answers = new HashMap<>();

	@PostConstruct
	private void init() {
		signingUrl = govSettings.getIntegration().getSignServer().getAddr() + "/sign";
		introduceUrl = govSettings.getIntegration().getSignServer().getAddr() + "/introduce";
		answerUrl = govSettings.getIntegration().getSignServer().getAddr() + "/answer";
		updateAccessToken();
	}

	private synchronized void updateAccessToken() {
		try {
			String privateKey = govSettings.getIntegration().getSignServer().getAppKey();
			SignServerIntroduceRequest request = new SignServerIntroduceRequest();
			request.setMyNameIs(govSettings.getIntegration().getSignServer().getAppName());

			RestApiResult<SignServerIntroduceResponse> introResult = requestPost(introduceUrl, request, SignServerIntroduceResponse.class);

			if(!introResult.isSuccess()) {
				logger.error("Cannot get signServerAccess  Token : {}, {}, {}", introResult.getResponseCode(), introResult.getErrorCode(), introResult.getErrorMessage());
				return;
			}

			String question = introResult.getData().getData().getQuestion();

			byte[] qBytes = Base64.getDecoder().decode(question);

			KeyPair keyPair = KeyPair.fromSecretSeed(privateKey);

			byte[] sBytes = keyPair.sign(qBytes);

			SignServerAnswerRequest request2 = new SignServerAnswerRequest();
			request2.setMyNameIs(govSettings.getIntegration().getSignServer().getAppName());
			request2.setYourQuestionWas(question);
			request2.setMyAnswerIs(Base64.getEncoder().encodeToString(sBytes));

			RestApiResult<SignServerAnswerResponse> answerResult = requestPost(answerUrl, request2, SignServerAnswerResponse.class);

			if(!answerResult.isSuccess()) {
				logger.error("Cannot get signServer Acces Token : {}, {}, {}", answerResult.getResponseCode(), answerResult.getErrorCode(), answerResult.getErrorMessage());
				return;
			}

			Map<String, String> newAnswers = new HashMap<>();
			for(Map.Entry<String, String> _kv : answerResult.getData().getData().getWelcomePackage().entrySet()) {
				byte[] kquestion = Base64.getDecoder().decode(_kv.getValue());
				byte[] kanswer = keyPair.sign(kquestion);

				newAnswers.put(_kv.getKey(), Base64.getEncoder().encodeToString(kanswer));
			}

			logger.info("Received new signServer Access Token");

			token = answerResult.getData().getData().getWelcomePresent();
			answers = newAnswers;

		} catch(Exception e) {
			token = null;
			answers = new HashMap<>();
			logger.exception(e);
		}
	}

	private RestApiResult<SignServerSignResponse> requestSign(String bc, String address, byte[] message) throws SigningException {
		if(token == null) updateAccessToken();

		RestApiResult<SignServerSignResponse> result = requestSign2(bc, address, message);

		// case of unauthorized result, mostly case of token expiration
		// try update access token and send request again
		if(result.getResponseCode() == HttpStatusCodes.STATUS_CODE_UNAUTHORIZED) {
			logger.debug("ss unauthorized, token might be expired, getting new one");
			updateAccessToken();

			result = requestSign2(bc, address, message);
		}

		return result;
	}

	private RestApiResult<SignServerSignResponse> requestSign2(String bc, String address, byte[] message) throws SigningException {
		if(token == null) {
			throw new SigningException(address, "Cannot request sign, access token is null");
		}

		if(!answers.containsKey(bc + ":" + address))
			throw new SigningException(address, "Cannot find ssk");

		SignServerSignRequest request = new SignServerSignRequest();
		request.setAddress(address);
		request.setType(bc);
		request.setData(ByteArrayUtils.toHexString(message));
		request.setAnswer(answers.get(bc + ":" + address));

		HttpHeaders headers = new HttpHeaders();
		headers.setAuthorization("Bearer " + token);
		return requestPost(signingUrl, headers, request, SignServerSignResponse.class);
	}

	public void signStellarTransaction(Transaction tx) throws SigningException {
		String accountId = tx.getSourceAccount().getAccountId();

		RestApiResult<SignServerSignResponse> signResult = requestSign("XLM", accountId, tx.hash());

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


	public byte[] signEthereumTransaction(RawTransaction tx, String from) throws SigningException {

		byte[] encodedTx = TransactionEncoder.encode(tx);

		byte[] hexMessage = Hash.sha3(encodedTx);

		RestApiResult<SignServerSignResponse> signResult = requestSign("ETH", from, hexMessage);

		byte[] sigBytes = ByteArrayUtils.fromHexString(signResult.getData().getData().getSignature());

		byte[] r = Arrays.copyOfRange(sigBytes, 0, 32);
		byte[] s = Arrays.copyOfRange(sigBytes, 32, 64);
		byte v = sigBytes[64];
		if(v != 27 && v != 28) {
			v = (byte) (27 + (v % 2));
		}

		Sign.SignatureData signatureData = new Sign.SignatureData(v, r, s);

		String messageKey;
		try {
			messageKey = Sign.signedMessageToKey(encodedTx, signatureData).toString(16);
		} catch(Exception ex) {
			throw new SigningException(ex, from, "verification failed (1)");
		}
		String signerAddress = Keys.getAddress(messageKey);
		if(!from.toLowerCase().equals("0x" + signerAddress.toLowerCase()))
			throw new SigningException(from, "verification failed (2)");


		List<RlpType> result = new ArrayList<>();

		result.add(RlpString.create(tx.getNonce()));
		result.add(RlpString.create(tx.getGasPrice()));
		result.add(RlpString.create(tx.getGasLimit()));

		// an empty to address (contract creation) should not be encoded as a numeric 0 value
		String to = tx.getTo();
		if(to != null && to.length() > 0) {
			// addresses that start with zeros should be encoded with the zeros included, not
			// as numeric values
			result.add(RlpString.create(Numeric.hexStringToByteArray(to)));
		} else {
			result.add(RlpString.create(""));
		}

		result.add(RlpString.create(tx.getValue()));

		// value field will already be hex encoded, so we need to convert into binary first
		byte[] data = Numeric.hexStringToByteArray(tx.getData());
		result.add(RlpString.create(data));

		if(signatureData != null) {
			result.add(RlpString.create(signatureData.getV()));
			result.add(RlpString.create(Bytes.trimLeadingZeroes(signatureData.getR())));
			result.add(RlpString.create(Bytes.trimLeadingZeroes(signatureData.getS())));
		}

		RlpList rlpList = new RlpList(result);
		return RlpEncoder.encode(rlpList);
	}
}
