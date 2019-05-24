package io.talken.dex.shared.service.blockchain.luniverse;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.exception.APIErrorException;
import io.talken.dex.shared.service.blockchain.RandomServerPicker;
import io.talken.dex.shared.service.blockchain.luniverse.dto.*;
import io.talken.dex.shared.service.integration.APIResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.utils.Convert;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class LuniverseNetworkService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(LuniverseNetworkService.class);

	private final DexSettings dexSettings;

	private final RandomServerPicker serverPicker = new RandomServerPicker();

	private LuniverseApiClient client;

	// TODO : move this to config or meta
	private final static String MTS = "FT9754";
	private final static String STS = "TALKP";

	@PostConstruct
	private void init() {
		logger.info("Using Luniverse PUBLIC Network.");
		for(String _s : dexSettings.getBcnode().getLuniverse().getServerList()) {
			logger.info("Luniverse API endpoint {} added.", _s);
			serverPicker.add(_s);
		}

		// luniverse has only one endpoint. This is intended
		client = new LuniverseApiClient(serverPicker.pick(), dexSettings.getBcnode().getLuniverse().getAux().getApiKey());
	}

	private String getIssuer() {
		return dexSettings.getBcnode().getLuniverse().getAux().getCompanyWallet();
	}

	private String getPrivateKey() {
		return dexSettings.getBcnode().getLuniverse().getAux().getCompanyWalletPrivateKey();
	}

	private String getPointBase() {
		return dexSettings.getBcnode().getLuniverse().getAux().getTalkp_base();
	}

	public LuniverseApiClient getClient() {
		return client;
	}

	public String createUserWallet(String userKey) throws APIErrorException {
		APIResult<LuniverseWalletResponse> result = getClient().createWallet(userKey);
		if(!result.isSuccess()) throw new APIErrorException(result);
		return result.getData().getData().getAddress();
	}

	public String getUserWallet(String userKey) throws APIErrorException {
		APIResult<LuniverseWalletResponse> result = getClient().getWallet(userKey);
		if(!result.isSuccess()) throw new APIErrorException(result);
		return result.getData().getData().getAddress();
	}

	public void issuePoint(String amount) throws APIErrorException {
		APIResult<LuniverseTransactionResponse> rtx = getClient().sendPoint(getIssuer(), getPointBase(), amount);
		if(!rtx.isSuccess()) throw new APIErrorException(rtx);

		APIResult<LuniverseResponse> sendr = getClient().submitSignedTx(rtx.getData().getData().getRawTx(), getPrivateKey());
		if(!sendr.isSuccess()) throw new APIErrorException(sendr);
	}

	public BigDecimal getTalkBalance(String address) throws APIErrorException {
		APIResult<LuniverseWalletBalanceResponse> balance = getClient().getWalletBalance(address, MTS);
		if(!balance.isSuccess()) throw new APIErrorException(balance);

		return Convert.fromWei(balance.getData().getData().getBalance(), Convert.Unit.ETHER);
	}

	public BigDecimal getPointBalance(String address) throws APIErrorException {
		APIResult<LuniverseWalletBalanceResponse> balance = getClient().getWalletBalance(address, MTS, STS);
		if(!balance.isSuccess()) throw new APIErrorException(balance);

		return Convert.fromWei(balance.getData().getData().getBalance(), Convert.Unit.ETHER);
	}

	public void distributePoint(String userKey, String amount) throws APIErrorException {
		APIResult<LuniverseTransactionResponse> result = getClient().sendPoint(getPointBase(), new LuniverseWalletRequest(userKey), amount);
		if(!result.isSuccess()) throw new APIErrorException(result);
	}

	public void reclaimPoint(String userKey, String amount) throws APIErrorException {
		APIResult<LuniverseTransactionResponse> result = getClient().sendPoint(new LuniverseWalletRequest(userKey), getPointBase(), amount);
		if(!result.isSuccess()) throw new APIErrorException(result);
	}
}
