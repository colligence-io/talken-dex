package io.talken.dex.shared.service.blockchain.ethereum;

import io.talken.common.RunningProfile;
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
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.ExecutionException;

/**
 * The type Ethereum network service.
 */
@Service
@Scope("singleton")
@RequiredArgsConstructor
public class EthereumNetworkService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumNetworkService.class);

	private final DexSettings dexSettings;

	private static final boolean ACTIVATE_LOCAL_NODE = false;

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

//		logger.info("Using Ethereum {} Network : {} {}", network, this.localClient.getUri(), this.localClient.getClientVersion());
		logger.info("Using Ethereum Infura {} Network : {} {}", network, this.infuraClient.getUri(), this.infuraClient.getClientVersion());
		updateGasPrice();
	}

    /**
     * local network rpc client
     * use this for monitoring, not for transaction
     *
     * @return local client
     */
    public EthRpcClient getLocalClient() {
        return getInfuraClient();
//		return this.localClient;
	}

    /**
     * infura network rpc client
     * use this for making transaction, not for monitoring
     *
     * @return infura client
     */
    public EthRpcClient getInfuraClient() {
		return this.infuraClient;
	}

    /**
     * Gets rpc client.
     *
     * @return the rpc client
     */
    public EthRpcClient getRpcClient() {
        if (ACTIVATE_LOCAL_NODE) return this.localClient;
	    else return this.infuraClient;
    }

    /**
     * Gets eth transaction.
     *
     * @param txHash the tx hash
     * @return the eth transaction
     * @throws ExecutionException   the execution exception
     * @throws InterruptedException the interrupted exception
     */
    public Transaction getEthTransaction(String txHash) throws ExecutionException, InterruptedException {
        Web3j web3j = this.getRpcClient().newClient();
        return web3j.ethGetTransactionByHash(txHash).sendAsync().get().getTransaction().orElse(null);
    }

    /**
     * Gets eth transaction receipt.
     *
     * @param txHash the tx hash
     * @return the eth transaction receipt
     * @throws ExecutionException   the execution exception
     * @throws InterruptedException the interrupted exception
     */
    public TransactionReceipt getEthTransactionReceipt(String txHash) throws ExecutionException, InterruptedException {
        Web3j web3j = this.getRpcClient().newClient();
        return web3j.ethGetTransactionReceipt(txHash).sendAsync().get().getTransactionReceipt().orElse(null);
    }

	/**
	 * update gasprice from oracle (ethGasStation Express)
	 */
    // 60 min = 1000 * 60 * 60
    @Scheduled(fixedDelay = 1000 * 60 * 60, initialDelay = 1000)
	private void updateGasPrice() {
        this.gasPrice = defaultGasPrice;
        String using = "DEFAULT";

	    // TODO : web3 gas staion api is unstable for prod
        if (RunningProfile.isProduction()) {
            // use ethescan
            try {
                GasPriceEtherscanResult.Result etscResult = queryEtscGasPrice().getResult();
//                BigDecimal proposal = gasConvert(etscResult.getProposeGasPrice());
                BigDecimal fast = gasConvert(etscResult.getFastGasPrice());
                BigDecimal tempGasPrice = BigDecimal.ZERO;
//                if (proposal.compareTo(tempGasPrice) > 0) {
//                    tempGasPrice = proposal;
//                    using = "PROPOSAL";
//                } else if (fast.compareTo(tempGasPrice) > 0) {
//                    tempGasPrice = fast;
//                    using = "FAST";
//                }

                // use only fast
                tempGasPrice = fast;
                using = "FAST";

                this.gasPrice = tempGasPrice;
                logger.info("Ethereum gasPrice updated : using GasPriceOracleResult [{}] = {}", using, this.gasPrice.toPlainString());
            } catch (IntegrationException e) {
                logger.error("Cannot query gasprice oracle service, use Default({} GWEI) as gasPrice", defaultGasP);
            }
        } else {
            // use oracle
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

                this.gasPrice = tempGasPrice;
                logger.info("Ethereum gasPrice updated : using GasPriceOracleResult [{}] = {}", using, this.gasPrice.toPlainString());

                // TODO : for dev or local
                this.gasPrice = BigDecimal.ONE;

            } catch(Exception ex) {
                logger.error("Cannot query gasprice oracle service, use Default({} GWEI) as gasPrice", defaultGasP);
            }
            logger.info("gasPrice.toPlainString() {} = gasPrice {}", this.gasPrice.toPlainString(), gasPrice);
        }
	}

    /**
     * get recommended gasPrice
     *
     * @param web3j the web 3 j
     * @return gas price
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

    /**
     * The type Gas price oracle result.
     */
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

    /**
     * The type Gas price etherscan result.
     */
    @Data
    public static class GasPriceEtherscanResult implements RestApiResponseInterface {
        private String status;
        private String message;
        private Result result;

        /**
         * The type Result.
         */
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

    /**
     * Gas convert big decimal.
     *
     * @param strNum the str num
     * @return the big decimal
     */
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
