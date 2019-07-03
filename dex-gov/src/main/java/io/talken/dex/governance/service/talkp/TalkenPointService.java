package io.talken.dex.governance.service.talkp;

import io.talken.common.exception.common.RestApiErrorException;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.RestApiResult;
import io.talken.dex.governance.service.integration.signer.SignServerService;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumSignInterface;
import io.talken.dex.shared.service.blockchain.luniverse.LuniverseApiClient;
import io.talken.dex.shared.service.blockchain.luniverse.LuniverseNetworkService;
import io.talken.dex.shared.service.blockchain.luniverse.dto.LuniverseRawTx;
import io.talken.dex.shared.service.blockchain.luniverse.dto.LuniverseRedeemPointRequest;
import io.talken.dex.shared.service.blockchain.luniverse.dto.LuniverseSendPointRequest;
import io.talken.dex.shared.service.blockchain.luniverse.dto.LuniverseTransactionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Convert;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;

@Service
@Scope("singleton")
public class TalkenPointService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TalkenPointService.class);

	@Autowired
	private DexSettings dexSettings;

	@Autowired
	private LuniverseNetworkService luniverseNetworkService;

	@Autowired
	private SignServerService ssService;

	private String MTS;
	private String STS;

	@PostConstruct
	private void init() {
		MTS = dexSettings.getBcnode().getLuniverse().getMtSymbol();
		STS = dexSettings.getBcnode().getLuniverse().getStSymbol();
	}

	private LuniverseApiClient getClient() {
		return luniverseNetworkService.getClient();
	}

	private String getIssuer() {
		return dexSettings.getBcnode().getLuniverse().getSecret().getCompanyWallet();
	}

	private String getPrivateKey() {
		return dexSettings.getBcnode().getLuniverse().getSecret().getCompanyWalletPrivateKey();
	}

	private String getPointBase() {
		return dexSettings.getBcnode().getLuniverse().getSecret().getTalkp_base();
	}

	public BigDecimal getTalkBalance(String userAddress) throws RestApiErrorException {
		return luniverseNetworkService.getBalance(userAddress, MTS);
	}

	public BigDecimal getPointBalance(String userAddress) throws RestApiErrorException {
		return luniverseNetworkService.getBalance(userAddress, MTS, STS);
	}

	public RestApiResult<String> issuePoint(String amount) throws RestApiErrorException {
		return sendPoint(getIssuer(), getPointBase(), amount);
	}

	public RestApiResult<String> distributePoint(String userAddress, String amount) throws RestApiErrorException {
		return sendPoint(getPointBase(), userAddress, amount);
	}

	public RestApiResult<String> reclaimPoint(String userAddress, String amount) throws RestApiErrorException {
		return sendPoint(userAddress, getPointBase(), amount);
	}

	public RestApiResult<LuniverseTransactionResponse> switchPoint(String userAddress, String amount) throws RestApiErrorException {
		return redeemPoint(userAddress, amount);
	}

	private RestApiResult<LuniverseTransactionResponse> redeemPoint(String address, String amount) throws RestApiErrorException {
		LuniverseRedeemPointRequest<String> request = new LuniverseRedeemPointRequest<>();
		request.setFrom(address);
		request.setAmount(Convert.toWei(amount, Convert.Unit.ETHER).toString());
		return getClient().requestTx("redeem_talkp", request);
	}

	private RestApiResult<String> sendPoint(String from, String to, String amount) throws RestApiErrorException {
		RestApiResult<String> result = new RestApiResult<>("sendPoint");

		try {
			LuniverseSendPointRequest<String, String> request = new LuniverseSendPointRequest<>();
			request.setFrom(from);
			request.setTo(to);
			request.setAmount(Convert.toWei(amount, Convert.Unit.ETHER).toString());

			RestApiResult<LuniverseTransactionResponse> ltxResult = getClient().requestTx("send_talkp", request);

			if(!ltxResult.isSuccess()) throw new RestApiErrorException(ltxResult);

			LuniverseRawTx rawTx = ltxResult.getData().getData().getRawTx();
			if(rawTx != null) {

				EthereumSignInterface signer;
				// if issuer, sign with private key
				if(from.equals(getIssuer())) {
					signer = (tx) -> TransactionEncoder.signMessage(tx, Credentials.create(getPrivateKey()));
				} else { // or else, assume from is managed from signServer
					signer = (tx) -> ssService.signEthereumTransaction(tx, from);
				}

				RestApiResult<EthSendTransaction> rtxResult = getClient().submitSignedTxViaRPC(rawTx, signer);

				if(!rtxResult.isSuccess()) throw new RestApiErrorException(rtxResult);

				result.setSuccess(true);
				result.setData(rtxResult.getData().getTransactionHash());
			} else {
				result.setSuccess(true);
				result.setData(ltxResult.getData().getData().getTxHash());
			}

		} catch(RestApiErrorException ex) {
			throw ex;
		} catch(Exception ex) {
			result.setSuccess(false);
			result.setException(ex);
			throw new RestApiErrorException(result);
		}

		return result;
	}
//
//
//	protected void test() throws Exception {
//		Web3j web3j = getClient().createRpcClient();
//
//		BigInteger amount = Convert.toWei("10", Convert.Unit.ETHER).toBigInteger();
//
//		LuniverseSendPointRequest<String, String> request = new LuniverseSendPointRequest<>();
//		request.setFrom(getIssuer());
//		logger.info(request.getFrom());
//		request.setTo(getPointBase());
//		request.setAmount(amount.toString());
//		APIResult<LuniverseTransactionResponse> ltxResult = getClient().requestTx("send_talkp", request);
//
//		BigInteger nonce = new BigInteger(Numeric.cleanHexPrefix(ltxResult.getData().getData().getRawTx().getNonce()), 16);
//		BigInteger gasPrice = new BigInteger(Numeric.cleanHexPrefix(ltxResult.getData().getData().getRawTx().getGasPrice()), 16);
//		BigInteger gasLimit = new BigInteger(Numeric.cleanHexPrefix(ltxResult.getData().getData().getRawTx().getGasLimit()), 16);
//
//		RawTransaction rawTx;
//
//		Function function = StandardERC20ContractFunctions.transfer("0xf3bb0ac384e706f57733db9e8c0e02d8b5df3519", amount);
//
//		String encodedFunction = FunctionEncoder.encode(function);
//
//		rawTx = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, "0x0567F057F6B93bDE83785996BfBB111725D51318", encodedFunction);
//
//		logger.info(JSONWriter.toJsonString(rawTx));
//
//		byte[] txSigned = TransactionEncoder.signMessage(rawTx, Credentials.create("9273946b86f55bd3f0d6a355b31d9f9f5068be851ca1db6a248fa3e3e7c4795e"));
//
//		logger.debug("Sending TX to ethereum network.");
//		EthSendTransaction ethSendTx = web3j.ethSendRawTransaction(Numeric.toHexString(txSigned)).sendAsync().get();
//
//		logger.info(JSONWriter.toJsonString(ethSendTx));
//
//	}
}
