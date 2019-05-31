package io.talken.dex.governance.service.talkp;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.integration.signer.SignServerService;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.exception.APIErrorException;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumSignInterface;
import io.talken.dex.shared.service.blockchain.luniverse.LuniverseApiClient;
import io.talken.dex.shared.service.blockchain.luniverse.LuniverseNetworkService;
import io.talken.dex.shared.service.blockchain.luniverse.dto.LuniverseRawTx;
import io.talken.dex.shared.service.blockchain.luniverse.dto.LuniverseRedeemPointRequest;
import io.talken.dex.shared.service.blockchain.luniverse.dto.LuniverseSendPointRequest;
import io.talken.dex.shared.service.blockchain.luniverse.dto.LuniverseTransactionResponse;
import io.talken.dex.shared.service.integration.APIResult;
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

	public BigDecimal getTalkBalance(String userAddress) throws APIErrorException {
		return luniverseNetworkService.getBalance(userAddress, MTS);
	}

	public BigDecimal getPointBalance(String userAddress) throws APIErrorException {
		return luniverseNetworkService.getBalance(userAddress, MTS, STS);
	}

	public APIResult<String> issuePoint(String amount) throws APIErrorException {
		return sendPoint(getIssuer(), getPointBase(), amount);
	}

	public APIResult<String> distributePoint(String userAddress, String amount) throws APIErrorException {
		return sendPoint(getPointBase(), userAddress, amount);
	}

	public APIResult<String> reclaimPoint(String userAddress, String amount) throws APIErrorException {
		return sendPoint(userAddress, getPointBase(), amount);
	}

	public APIResult<LuniverseTransactionResponse> switchPoint(String userAddress, String amount) throws APIErrorException {
		return redeemPoint(userAddress, amount);
	}

	private APIResult<LuniverseTransactionResponse> redeemPoint(String address, String amount) throws APIErrorException {
		LuniverseRedeemPointRequest<String> request = new LuniverseRedeemPointRequest<>();
		request.setFrom(address);
		request.setAmount(Convert.toWei(amount, Convert.Unit.ETHER).toString());
		return getClient().requestTx("redeem_talkp", request);
	}

	private APIResult<String> sendPoint(String from, String to, String amount) throws APIErrorException {
		APIResult<String> result = new APIResult<>("sendPoint");

		try {
			LuniverseSendPointRequest<String, String> request = new LuniverseSendPointRequest<>();
			request.setFrom(from);
			request.setTo(to);
			request.setAmount(Convert.toWei(amount, Convert.Unit.ETHER).toString());

			APIResult<LuniverseTransactionResponse> ltxResult = getClient().requestTx("send_talkp", request);

			if(!ltxResult.isSuccess()) throw new APIErrorException(ltxResult);

			LuniverseRawTx rawTx = ltxResult.getData().getData().getRawTx();
			if(rawTx != null) {

				EthereumSignInterface signer;
				// if issuer, sign with private key
				if(from.equals(getIssuer())) {
					signer = (tx) -> TransactionEncoder.signMessage(tx, Credentials.create(getPrivateKey()));
				} else { // or else, assume from is managed from signServer
					signer = (tx) -> ssService.signEthereumTransaction(tx, from);
				}

				APIResult<EthSendTransaction> rtxResult = getClient().submitSignedTxViaRPC(rawTx, signer);

				if(!rtxResult.isSuccess()) throw new APIErrorException(rtxResult);

				result.setSuccess(true);
				result.setData(rtxResult.getData().getTransactionHash());
			} else {
				result.setSuccess(true);
				result.setData(ltxResult.getData().getData().getTxHash());
			}

		} catch(APIErrorException ex) {
			throw ex;
		} catch(Exception ex) {
			result.setSuccess(false);
			result.setException(ex);
			throw new APIErrorException(result);
		}

		return result;
	}
}
