package io.talken.dex.shared.service.blockchain.ethereum;


import io.talken.common.exception.common.IntegrationException;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.IntegrationResult;
import io.talken.common.util.integration.rest.RestApiClient;
import io.talken.common.util.integration.rest.RestApiResponseInterface;
import io.talken.dex.shared.DexSettings;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.utils.Convert;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class EthereumNetworkService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumNetworkService.class);

	private final DexSettings dexSettings;

	private String serverUri;

	private boolean isParity = false;

	private String gasOracleApiUrl;

	private static final BigDecimal defaultGasPrice = BigDecimal.valueOf(20); // default 20GWEI
	private BigDecimal gasPrice = defaultGasPrice;

	@PostConstruct
	private void init() throws Exception {
		final String network = dexSettings.getBcnode().getEthereum().getNetwork().equalsIgnoreCase("test") ? "TEST" : "PUBLIC";
		this.serverUri = dexSettings.getBcnode().getEthereum().getRpcUri();
		final String client = newClient().web3ClientVersion().send().getWeb3ClientVersion();
		this.isParity = client.startsWith("Parity-Ethereum");
		this.gasOracleApiUrl = dexSettings.getBcnode().getEthereum().getGasOracleUrl();
		logger.info("Using Ethereum {} Network : {} {}", network, this.serverUri, client);
		updateGasPrice();
	}

	public Web3j newClient() {
		return Web3j.build(newWeb3jService());
	}

	public Web3jService newWeb3jService() {
		return new Web3jHttpService(this.serverUri);
	}

	@Scheduled(fixedDelay = 5000)
	private void updateGasPrice() {
		try {
			GasPriceOracleResult result = queryGasPrice();
			if(this.gasPrice.compareTo(result.getStandard()) != 0) {
				this.gasPrice = result.getStandard();
				logger.info("Ethereum gasPrice updated : using STANDARD = {}", this.gasPrice.toPlainString());
			}
		} catch(Exception ex) {
			this.gasPrice = defaultGasPrice;
			logger.error("Cannot query gasprice oracle service, use 20 GWEI as gasPrice");
		}
	}

	public BigInteger getNonce(Web3jService web3jService, String address) throws Exception {
		if(this.isParity) {
			Request<?, ParityNextNonceResponse> nonceReq = new Request<>("parity_nextNonce", Collections.singletonList(address), web3jService, ParityNextNonceResponse.class);
			return nonceReq.send().getNextNonce();
		} else {
			return Web3j.build(web3jService).ethGetTransactionCount(address, DefaultBlockParameterName.PENDING).sendAsync().get().getTransactionCount();
		}
	}

	public BigInteger getGasPrice(Web3j web3j) {
		if(gasPrice != null) {
			return Convert.toWei(gasPrice.toPlainString(), Convert.Unit.GWEI).toBigInteger();
		} else {
			return Convert.toWei(defaultGasPrice.toPlainString(), Convert.Unit.GWEI).toBigInteger();
		}
	}

	private GasPriceOracleResult queryGasPrice() throws IntegrationException {
		IntegrationResult<GasPriceOracleResult> result = RestApiClient.requestGet(this.gasOracleApiUrl, GasPriceOracleResult.class);
		if(result.isSuccess()) {
			return result.getData();
		} else {
			throw new IntegrationException(result);
		}
	}

	@Data
	public static class GasPriceOracleResult implements RestApiResponseInterface {
		private BigDecimal safeLow;
		private BigDecimal standard;
		private BigDecimal fast;
		private BigDecimal fastest;
		private BigDecimal block_time;
		private BigInteger blockNum;

		@Override
		public boolean checkHttpResponse(int httpStatus) {
			return RestApiResponseInterface.standardHttpSuccessCheck(httpStatus);
		}

		@Override
		public boolean checkResult() {
			return true;
		}

		@Override
		public String resultCode() {
			return "OK";
		}

		@Override
		public String resultMessage() {
			return "OK";
		}
	}
}
