package io.talken.dex.shared.service.integration.signer;

import com.google.api.client.http.HttpHeaders;
import io.talken.common.util.ByteArrayUtils;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.IntegrationResult;
import io.talken.common.util.integration.rest.RestApiClient;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.shared.exception.SigningException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Talken Sign Server(tss) Service
 */
public class SignServerService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(SignServerService.class);

	@Autowired
	private AdminAlarmService adminAlarmService;

	private String signingUrl;
	private String introduceUrl;
	private String answerUrl;
	private KeyPair appKey;
	private String appName;

	private SignServerAccessToken accessToken = new SignServerAccessToken();

	/**
	 * lock for updating sign server access token (token should be singleton)
	 */
	private final static Object updateLock = new Object();

	public SignServerService(String serverAddr, String appName, String appKey) {
		this.signingUrl = serverAddr + "/sign";
		this.introduceUrl = serverAddr + "/introduce";
		this.answerUrl = serverAddr + "/answer";
		this.appKey = KeyPair.fromSecretSeed(appKey);
		this.appName = appName;

		if(!updateAccessToken()) {
			throw new IllegalStateException("Cannot get signServer access token");
		}
	}

	@Scheduled(fixedDelay = 1000, initialDelay = 3000)
	private void checkAccessToken() {
		synchronized(updateLock) {
			if(!accessToken.isValid() || accessToken.needsUpdate()) {
				if(!updateAccessToken()) {
					adminAlarmService.error(logger, "Cannot Update SignServer Token");
				}
			}
		}
	}

	/**
	 * update access token
	 *
	 * @return
	 */
	private synchronized boolean updateAccessToken() {
		try {
			SignServerIntroduceRequest request = new SignServerIntroduceRequest();
			request.setMyNameIs(this.appName);

			IntegrationResult<SignServerIntroduceResponse> introResult = RestApiClient.requestPost(this.introduceUrl, request, SignServerIntroduceResponse.class);

			if(!introResult.isSuccess()) {
				logger.error("Cannot get signServerAccess  Token : {}, {}", introResult.getErrorCode(), introResult.getErrorMessage());
				return false;
			}

			String question = introResult.getData().getData().getQuestion();

			byte[] qBytes = Base64.getDecoder().decode(question);
			byte[] sBytes = this.appKey.sign(qBytes);

			SignServerAnswerRequest request2 = new SignServerAnswerRequest();
			request2.setMyNameIs(this.appName);
			request2.setYourQuestionWas(question);
			request2.setMyAnswerIs(Base64.getEncoder().encodeToString(sBytes));

			IntegrationResult<SignServerAnswerResponse> answerResult = RestApiClient.requestPost(this.answerUrl, request2, SignServerAnswerResponse.class);

			if(!answerResult.isSuccess()) {
				logger.error("Cannot get signServer Access Token : {}, {}", answerResult.getErrorCode(), answerResult.getErrorMessage());
				return false;
			}

			Map<String, String> newAnswers = new HashMap<>();
			for(Map.Entry<String, String> _kv : answerResult.getData().getData().getWelcomePackage().entrySet()) {
				byte[] kquestion = Base64.getDecoder().decode(_kv.getValue());
				byte[] kanswer = this.appKey.sign(kquestion);

				newAnswers.put(_kv.getKey(), Base64.getEncoder().encodeToString(kanswer));
			}

			SignServerAccessToken newToken = new SignServerAccessToken();
			newToken.setToken(answerResult.getData().getData().getWelcomePresent());
			newToken.setTokenExpires(answerResult.getData().getData().getExpires());
			newToken.setAnswers(newAnswers);
			this.accessToken = newToken;

			logger.info("Received new signServer Access Token, expires in {} secs, will update in {} secs", newToken.getRemainedTTL(), newToken.getRemainedTBU());
			return true;
		} catch(Exception e) {
			this.accessToken = new SignServerAccessToken();
			logger.exception(e);
			return false;
		}
	}

	/**
	 * request sign to tss
	 *
	 * @param bc
	 * @param address
	 * @param message
	 * @return
	 * @throws SigningException
	 */
	private IntegrationResult<SignServerSignResponse> requestSign(String bc, String address, byte[] message) throws SigningException {
		synchronized(updateLock) {
			if(this.accessToken == null) {
				throw new SigningException(address, "Cannot request sign, access token is not valid");
			}
		}

		SignServerSignRequest request = new SignServerSignRequest();
		request.setAddress(address);
		request.setType(bc);
		request.setData(ByteArrayUtils.toHexString(message));
		request.setAnswer(this.accessToken.getAnswers().get(bc + ":" + address));

		HttpHeaders headers = new HttpHeaders();
		headers.setAuthorization("Bearer " + this.accessToken.getToken());
		return RestApiClient.requestPost(this.signingUrl, headers, request, SignServerSignResponse.class);
	}

	/**
	 * sign stellar tx envelope
	 * shortcut for simple transaction that tx.getSourceAccount is only account to be signed
	 *
	 * @param tx
	 * @throws SigningException
	 */
	public void signStellarTransaction(Transaction tx) throws SigningException {
		signStellarTransaction(tx, tx.getSourceAccount());
	}

	/**
	 * sign stellar tx envelope with given accountId
	 *
	 * @param tx
	 * @param accountId
	 * @throws SigningException
	 */
	public void signStellarTransaction(Transaction tx, String accountId) throws SigningException {
		IntegrationResult<SignServerSignResponse> signResult = requestSign("XLM", accountId, tx.hash());

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
			PublicKey.encode(xdrOutputStream, KeyPair.fromAccountId(accountId).getXdrPublicKey());
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

	/**
	 * sign ethereum raw tx
	 *
	 * @param tx
	 * @param from
	 * @return
	 * @throws SigningException
	 */
	public byte[] signEthereumTransaction(RawTransaction tx, String from) throws SigningException {

		byte[] encodedTx = TransactionEncoder.encode(tx);

		byte[] hexMessage = Hash.sha3(encodedTx);

		IntegrationResult<SignServerSignResponse> signResult = requestSign("ETH", from, hexMessage);

		if(!signResult.isSuccess()) {
			throw new SigningException(from, signResult.getErrorCode() + " : " + signResult.getErrorMessage());
		}

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
