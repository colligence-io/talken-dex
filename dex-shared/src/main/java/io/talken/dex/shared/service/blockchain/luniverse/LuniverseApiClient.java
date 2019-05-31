package io.talken.dex.shared.service.blockchain.luniverse;

import com.google.api.client.http.HttpHeaders;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumSignInterface;
import io.talken.dex.shared.service.blockchain.luniverse.dto.*;
import io.talken.dex.shared.service.integration.APIResult;
import io.talken.dex.shared.service.integration.AbstractRestApiService;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public class LuniverseApiClient extends AbstractRestApiService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(LuniverseApiClient.class);

	private final String apiUri;
	private final String apiKey;
	private final String rpcUri;

	private static final String LUNIVERSE_PKMS_WALLET = "LUNIVERSE";

	public LuniverseApiClient(String apiUri, String apiKey, String rpcUri) {
		this.apiUri = apiUri;
		this.apiKey = apiKey;
		this.rpcUri = rpcUri;
	}

	public Web3j createRpcClient() {
		return Web3j.build(new HttpService(rpcUri));
	}

	private HttpHeaders authHeader() {
		HttpHeaders headers = new HttpHeaders();
		headers.setAuthorization("Bearer " + apiKey);
		return headers;
	}

	public APIResult<LuniverseWalletResponse> createWallet(String userKey) {
		logger.debug("send create {} wallet request for {} to luniverse API", LUNIVERSE_PKMS_WALLET, userKey);
		return requestPost(this.apiUri + "/tx/v1.0/wallets", authHeader(), new LuniverseWalletRequest(LUNIVERSE_PKMS_WALLET, userKey), LuniverseWalletResponse.class);
	}

	public APIResult<LuniverseWalletResponse> getWallet(String userKey) {
		Map<String, String> request = new HashMap<>();
		request.put("walletType", LUNIVERSE_PKMS_WALLET);
		request.put("userKey", userKey);

		logger.debug("send wallet bridge request for {} to luniverse API", userKey);
		return requestGet(this.apiUri + "/tx/v1.0/wallets/bridge", authHeader(), request, LuniverseWalletResponse.class);
	}

	public APIResult<LuniverseWalletBalanceResponse> getWalletBalance(String address, String mainTokenSymbol) {
		return getWalletBalance(address, mainTokenSymbol, null);
	}

	public APIResult<LuniverseWalletBalanceResponse> getWalletBalance(String address, String mainTokenSymbol, String sideTokenSymbol) {
		StringBuilder sb = new StringBuilder(this.apiUri).append("/tx/v1.0/wallets/").append(address).append("/").append(mainTokenSymbol);
		if(sideTokenSymbol != null) sb.append("/").append(sideTokenSymbol);
		sb.append("/balance");

		logger.debug("send {} {} balance request for {} to luniverse API", mainTokenSymbol, sideTokenSymbol, address);
		return requestGet(sb.toString(), authHeader(), null, LuniverseWalletBalanceResponse.class);
	}

	public APIResult<LuniverseTransactionResponse> requestTx(String txName, Object request) {
		logger.debug("send {} transaction request to luniverse API", txName);
		return requestPost(this.apiUri + "/tx/v1.0/transactions/" + txName, authHeader(), request, LuniverseTransactionResponse.class);
	}

	public APIResult<EthSendTransaction> submitSignedTxViaRPC(LuniverseRawTx rawTx, EthereumSignInterface signer) {
		APIResult<EthSendTransaction> result = new APIResult<>("submitSignedTxViaRPC");

		try {
			RawTransaction tx = RawTransaction.createTransaction(
					new BigInteger(Numeric.cleanHexPrefix(rawTx.getNonce()), 16),
					new BigInteger(Numeric.cleanHexPrefix(rawTx.getGasPrice()), 16),
					new BigInteger(Numeric.cleanHexPrefix(rawTx.getGasLimit()), 16),
					rawTx.getTo(),
					rawTx.getData()
			);

			byte[] signedTx = signer.sign(tx);

			logger.debug("Sending signed TX to luniverse RPC.");
			Web3j web3j = createRpcClient();
			EthSendTransaction ethSendTx = web3j.ethSendRawTransaction(Numeric.toHexString(signedTx)).sendAsync().get();

			Response.Error error = ethSendTx.getError();

			if(error == null) {
				result.setSuccess(true);
				result.setData(ethSendTx);
			} else {
				result.setSuccess(false);
				result.setError(Integer.toString(error.getCode()), error.getMessage());
			}
		} catch(Exception ex) {
			logger.exception(ex);
			result.setSuccess(false);
			result.setException(ex);
		}

		return result;
	}
}
