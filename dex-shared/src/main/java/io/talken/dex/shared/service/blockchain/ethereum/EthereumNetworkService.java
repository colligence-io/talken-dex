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
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.utils.Convert;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class EthereumNetworkService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumNetworkService.class);

    private final Environment environment;

	private final DexSettings dexSettings;

	private EthRpcClient localClient;
	private EthRpcClient infuraClient;

	private String gasOracleApiUrl;
    private String gasEthescanApiUrl;

    private static final int defaultGasP = 100;
	private static final BigDecimal defaultGasPrice = BigDecimal.valueOf(defaultGasP); // default 20 -> 100 GWEI
	private BigDecimal gasPrice = defaultGasPrice;

	@PostConstruct
	private void init() throws Exception {
		final String network = dexSettings.getBcnode().getEthereum().getNetwork().equalsIgnoreCase("test") ? "TEST" : "PUBLIC";

		this.localClient = new EthRpcClient(dexSettings.getBcnode().getEthereum().getRpcUri());
		this.infuraClient = new EthRpcClient(dexSettings.getBcnode().getEthereum().getInfuraUri());
		this.gasOracleApiUrl = dexSettings.getBcnode().getEthereum().getGasOracleUrl();
		this.gasEthescanApiUrl = dexSettings.getBcnode().getEthereum().getGasEtherscanUrl();

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
	@Scheduled(fixedDelay = 8000)
	private void updateGasPrice() {
	    boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("production");
        this.gasPrice = defaultGasPrice;
        String using = "DEFAULT";

	    // TODO : web3 gas staion api is unstable for prod
        if (isProd) {
            try {
                GasPriceEtherscanResult.Result etscResult = queryEtscGasPrice().getResult();
                BigDecimal proposal = gasConvert(etscResult.getProposeGasPrice());
                BigDecimal fast = gasConvert(etscResult.getFastGasPrice());
                BigDecimal tempGasPrice = BigDecimal.ZERO;

                if (proposal.compareTo(tempGasPrice) > 0) {
                    tempGasPrice = proposal;
                    using = "PROPOSAL";
                } else if (fast.compareTo(tempGasPrice) > 0) {
                    tempGasPrice = fast;
                    using = "FAST";
                }

                if(this.gasPrice.compareTo(tempGasPrice) < 0) {
                    this.gasPrice = tempGasPrice;
                    logger.info("Ethereum gasPrice updated : using GasPriceEtherscanResult [{}] = {}", using, this.gasPrice.toPlainString());
                } else {
                    using = "DEFAULT";
                }
                logger.info("tempGasPrice [{}] {} = gasPrice {}", using, this.gasPrice.toPlainString(), gasPrice);

            } catch (IntegrationException e) {
                logger.error("Cannot query gasprice oracle service, use Default({} GWEI) as gasPrice", defaultGasP);
            }
        } else {
            try {
                GasPriceOracleResult result = queryGasPrice();
                BigDecimal standard = gasConvert(result.getStandard());
                BigDecimal fast = gasConvert(result.getFast());
                BigDecimal fastest = gasConvert(result.getFastest());
                BigDecimal tempGasPrice = BigDecimal.ZERO;

                if (standard.compareTo(tempGasPrice) > 0) {
                    tempGasPrice = standard;
                    using = "STANDARD";
                } else if (fast.compareTo(tempGasPrice) > 0) {
                    tempGasPrice = fast;
                    using = "FAST";
                } else if (fastest.compareTo(tempGasPrice) > 0) {
                    tempGasPrice = fastest;
                    using = "FASTEST";
                }

                if(this.gasPrice.compareTo(tempGasPrice) < 0) {
                    this.gasPrice = tempGasPrice;
                    logger.info("Ethereum gasPrice updated : using GasPriceOracleResult [{}] = {}", using, this.gasPrice.toPlainString());
                } else {
                    using = "DEFAULT";
                }

                logger.info("tempGasPrice [{}] {} = gasPrice {}", using, this.gasPrice.toPlainString(), gasPrice);

                // TODO : for dev or local
                this.gasPrice = BigDecimal.ONE;

            } catch(Exception ex) {
                logger.error("Cannot query gasprice oracle service, use Default({} GWEI) as gasPrice", defaultGasP);
            }
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

    private GasPriceEtherscanResult queryEtscGasPrice() throws IntegrationException {
        IntegrationResult<GasPriceEtherscanResult> result = RestApiClient.requestGet(this.gasEthescanApiUrl, GasPriceEtherscanResult.class);
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

    @Data
    public static class GasPriceEtherscanResult implements RestApiResponseInterface {
        private String status;
        private String message;
        private Result result;

        @Data
        class Result {
            private String LastBlock;
            private String SafeGasPrice;
            private String ProposeGasPrice;
            private String FastGasPrice;
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
            return this.status;
        }

        @Override
        public String resultMessage() {
            return this.message;
        }
    }

    public BigDecimal gasConvert(String strNum) {
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
}
