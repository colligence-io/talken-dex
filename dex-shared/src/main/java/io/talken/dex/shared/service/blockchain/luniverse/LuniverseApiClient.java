package io.talken.dex.shared.service.blockchain.luniverse;

import com.google.api.client.http.HttpHeaders;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.service.blockchain.luniverse.dto.*;
import io.talken.dex.shared.service.integration.APIResult;
import io.talken.dex.shared.service.integration.AbstractRestApiService;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public class LuniverseApiClient extends AbstractRestApiService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(LuniverseApiClient.class);

	private final String apiUri;
	private final String apiKey;

	public static final String LUNIVERSE_PKMS_WALLET = "LUNIVERSE";

	public LuniverseApiClient(String apiUri, String apiKey) {
		this.apiUri = apiUri;
		this.apiKey = apiKey;
	}

	private HttpHeaders authHeader() {
		HttpHeaders headers = new HttpHeaders();
		headers.setAuthorization("Bearer " + apiKey);
		return headers;
	}

	public APIResult<LuniverseWalletResponse> createWallet(String userKey) {
		return requestPost(this.apiUri + "/tx/v1.0/wallets", authHeader(), new LuniverseWalletRequest(userKey), LuniverseWalletResponse.class);
	}

	public APIResult<LuniverseWalletResponse> getWallet(String userKey) {
		Map<String, String> request = new HashMap<>();
		request.put("walletType", LUNIVERSE_PKMS_WALLET);
		request.put("userKey", userKey);

		return requestGet(this.apiUri + "/tx/v1.0/wallets/bridge", authHeader(), request, LuniverseWalletResponse.class);
	}

	public APIResult<LuniverseWalletBalanceResponse> getWalletBalance(String address, String mainTokenSymbol) {
		return getWalletBalance(address, mainTokenSymbol, null);
	}

	public APIResult<LuniverseWalletBalanceResponse> getWalletBalance(String address, String mainTokenSymbol, String sideTokenSymbol) {
		StringBuilder sb = new StringBuilder(this.apiUri).append("/tx/v1.0/wallets/").append(address).append("/").append(mainTokenSymbol);
		if(sideTokenSymbol != null) sb.append("/").append(sideTokenSymbol);
		sb.append("/balance");

		return requestGet(sb.toString(), authHeader(), null, LuniverseWalletBalanceResponse.class);
	}

	public APIResult<LuniverseTransactionResponse> sendPoint(String from, String to, String amount) {
		LuniverseSendPointRequest<String, String> request = new LuniverseSendPointRequest<>();
		request.setFrom(from);
		request.setTo(to);
		request.setAmount(Convert.toWei(amount, Convert.Unit.ETHER).toString());

		return requestPost(this.apiUri + "/tx/v1.0/transactions/send_talkp", authHeader(), request, LuniverseTransactionResponse.class);
	}

	public APIResult<LuniverseTransactionResponse> sendPoint(String from, LuniverseWalletRequest to, String amount) {
		LuniverseSendPointRequest<String, LuniverseWalletRequest> request = new LuniverseSendPointRequest<>();
		request.setFrom(from);
		request.setTo(to);
		request.setAmount(Convert.toWei(amount, Convert.Unit.ETHER).toString());

		return requestPost(this.apiUri + "/tx/v1.0/transactions/send_talkp", authHeader(), request, LuniverseTransactionResponse.class);
	}

	public APIResult<LuniverseTransactionResponse> sendPoint(LuniverseWalletRequest from, String to, String amount) {
		LuniverseSendPointRequest<LuniverseWalletRequest, String> request = new LuniverseSendPointRequest<>();
		request.setFrom(from);
		request.setTo(to);
		request.setAmount(Convert.toWei(amount, Convert.Unit.ETHER).toString());

		return requestPost(this.apiUri + "/tx/v1.0/transactions/send_talkp", authHeader(), request, LuniverseTransactionResponse.class);
	}

	public APIResult<LuniverseResponse> submitSignedTx(LuniverseRawTx rawTx, String privateKey) {
		RawTransaction tx = RawTransaction.createTransaction(
				new BigInteger(Numeric.cleanHexPrefix(rawTx.getNonce()), 16),
				new BigInteger(Numeric.cleanHexPrefix(rawTx.getGasPrice()), 16),
				new BigInteger(Numeric.cleanHexPrefix(rawTx.getGasLimit()), 16),
				rawTx.getTo(),
				rawTx.getData()
		);

		byte[] signedTx = TransactionEncoder.signMessage(tx, Credentials.create(privateKey));
		LuniverseSubmitSignedTxRequest request = new LuniverseSubmitSignedTxRequest();
		request.setSignedTx(Numeric.cleanHexPrefix(Numeric.toHexString(signedTx)));

		return requestPost(this.apiUri + "/tx/v1.0/transactions/send_talkp", authHeader(), request, LuniverseResponse.class);
	}

	public APIResult<LuniverseTransactionResponse> redeemPoint(LuniverseWalletRequest from, String amount) {
		LuniverseRedeemPointRequest<LuniverseWalletRequest> request = new LuniverseRedeemPointRequest<>();
		request.setFrom(from);
		request.setAmount(Convert.toWei(amount, Convert.Unit.ETHER).toString());

		return requestPost(this.apiUri + "/tx/v1.0/transactions/redeem_talkp", authHeader(), request, LuniverseTransactionResponse.class);
	}

	public APIResult<LuniverseTransactionResponse> redeemPoint(String from, String amount) {
		LuniverseRedeemPointRequest<String> request = new LuniverseRedeemPointRequest<>();
		request.setFrom(from);
		request.setAmount(Convert.toWei(amount, Convert.Unit.ETHER).toString());

		return requestPost(this.apiUri + "/tx/v1.0/transactions/redeem_talkp", authHeader(), request, LuniverseTransactionResponse.class);
	}
}
