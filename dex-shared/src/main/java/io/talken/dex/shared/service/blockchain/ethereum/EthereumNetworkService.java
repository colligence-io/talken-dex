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
import org.web3j.utils.Convert;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class EthereumNetworkService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumNetworkService.class);

	private final DexSettings dexSettings;

	private EthRpcClient localClient;
	private EthRpcClient infuraClient;

	private String gasOracleApiUrl;

	private static final BigDecimal defaultGasPrice = BigDecimal.valueOf(100); // default 20 -> 100 GWEI
	private BigDecimal gasPrice = defaultGasPrice;

	@PostConstruct
	private void init() throws Exception {
		final String network = dexSettings.getBcnode().getEthereum().getNetwork().equalsIgnoreCase("test") ? "TEST" : "PUBLIC";

		this.localClient = new EthRpcClient(dexSettings.getBcnode().getEthereum().getRpcUri());
		this.infuraClient = new EthRpcClient(dexSettings.getBcnode().getEthereum().getInfuraUri());
		this.gasOracleApiUrl = dexSettings.getBcnode().getEthereum().getGasOracleUrl();
		logger.info("Using Ethereum {} Network : {} {}", network, this.localClient.getUri(), this.localClient.getClientVersion());
		logger.info("Using Ethereum Infura {} Network : {} {}", network, this.infuraClient.getUri(), this.infuraClient.getClientVersion());
		updateGasPrice();
	}

	/**
	 * local network rpc client
	 * use this for monitoring, not for transaction
	 *
	 * @return
	 */
	public EthRpcClient getLocalClient() {
		return this.localClient;
	}

	/**
	 * infura network rpc client
	 * use this for making transaction, not for monitoring
	 *
	 * @return
	 */
	public EthRpcClient getInfuraClient() {
		return this.infuraClient;
	}

	/**
	 * update gasprice from oracle (ethGasStation Express)
	 */
	@Scheduled(fixedDelay = 5000)
	private void updateGasPrice() {
		try {
			GasPriceOracleResult result = queryGasPrice();
			BigDecimal standard = GasPriceOracleResult.convert(result.getStandard());
			if(this.gasPrice.compareTo(standard) != 0) {
				this.gasPrice = standard;
				logger.info("Ethereum gasPrice updated : using STANDARD = {}", this.gasPrice.toPlainString());
			}
		} catch(Exception ex) {
			this.gasPrice = defaultGasPrice;
			logger.error("Cannot query gasprice oracle service, use 20 GWEI as gasPrice");
		}
	}

	/**
	 * get recommended gasPrice
	 *
	 * @param web3j
	 * @return
	 */
	public BigInteger getGasPrice(Web3j web3j) {
		if(gasPrice != null) {
			return Convert.toWei(gasPrice.toPlainString(), Convert.Unit.GWEI).toBigInteger();
		} else {
			return Convert.toWei(defaultGasPrice.toPlainString(), Convert.Unit.GWEI).toBigInteger();
		}
	}

	/**
	 * query oracle for recommended gas price
	 *
	 * @return
	 * @throws IntegrationException
	 */
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
		private String safeLow;
		private String standard;
		private String fast;
		private String fastest;
		private String block_time;
		private String blockNum;

		public static BigDecimal convert(String strNum) {
            if (strNum == null) {
                return BigDecimal.ZERO;
            }
            try {
                Double.parseDouble(strNum);
                return new BigDecimal(strNum);
            } catch (NumberFormatException nfe) {
                return BigDecimal.ZERO;
            }
        }

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
