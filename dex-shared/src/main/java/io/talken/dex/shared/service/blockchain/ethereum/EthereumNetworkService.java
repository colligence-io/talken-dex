package io.talken.dex.shared.service.blockchain.ethereum;


import io.talken.common.exception.common.RestApiErrorException;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.AbstractRestApiService;
import io.talken.common.util.integration.RestApiResponseInterface;
import io.talken.common.util.integration.RestApiResult;
import io.talken.dex.shared.DexSettings;
import io.talken.dex.shared.service.blockchain.RandomServerPicker;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.BigInteger;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class EthereumNetworkService extends AbstractRestApiService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumNetworkService.class);

	private final DexSettings dexSettings;

	private final RandomServerPicker serverPicker = new RandomServerPicker();

	private String gasOracleApiUrl;

	private GasPriceOracleResult gasOracleResult = null;

	@PostConstruct
	private void init() {
		if(dexSettings.getBcnode().getEthereum().getNetwork().equalsIgnoreCase("test")) {
			logger.info("Using Ethereum TEST Network.");
		} else {
			logger.info("Using Ethereum PUBLIC Network.");
		}
		for(String _s : dexSettings.getBcnode().getEthereum().getServerList()) {
			logger.info("Ethereum jsonrpc endpoint {} added.", _s);
			serverPicker.add(_s);
		}
		this.gasOracleApiUrl = dexSettings.getBcnode().getEthereum().getGasOracleUrl();
		updateGasPrice();
	}

	public Web3j newClient() {
		return Web3j.build(new HttpService(serverPicker.pick()));
	}

	@Scheduled(fixedDelay = 15000)
	private void updateGasPrice() {
		try {
			this.gasOracleResult = queryGasPrice();
			logger.trace("GasPrice updated : using FAST = {}", this.gasOracleResult.getFast().toPlainString());
		} catch(Exception ex) {
			this.gasOracleResult = null;
			logger.error("Cannot query gasprice oracle service, use 20 GWEI as gasPrice");
		}
	}

	public BigInteger getGasPrice(Web3j web3j) {
		if(gasOracleResult != null) {
			return Convert.toWei(gasOracleResult.fast.toPlainString(), Convert.Unit.GWEI).toBigInteger();
		} else {
			return Convert.toWei("20", Convert.Unit.GWEI).toBigInteger();
		}
	}

	public BigInteger getGasLimit(Web3j web3j) {
		// TODO : calculate or get proper value
		return new BigInteger("250000");

//		EthBlock.Block lastBlock = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock();
//		return lastBlock.getGasLimit();
	}

	private GasPriceOracleResult queryGasPrice() throws RestApiErrorException {
		RestApiResult<GasPriceOracleResult> result = requestGet(this.gasOracleApiUrl, GasPriceOracleResult.class);
		if(result.isSuccess()) {
			return result.getData();
		} else {
			throw new RestApiErrorException(result);
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
		public boolean isSuccess() {
			return true;
		}

		@Override
		public String getCode() {
			return "OK";
		}

		@Override
		public String getMessage() {
			return "OK";
		}
	}
}
