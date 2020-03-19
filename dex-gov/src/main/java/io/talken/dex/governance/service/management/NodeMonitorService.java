package io.talken.dex.governance.service.management;


import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.collection.ObjectPair;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.shared.service.blockchain.ethereum.EthRpcClient;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumNetworkService;
import io.talken.dex.shared.service.blockchain.luniverse.LuniverseNetworkService;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.stellar.sdk.responses.RootResponse;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;

import java.io.IOException;
import java.math.BigInteger;

/**
 * Monitor BC Node Server status
 * NOTE this service is very-hard-coded
 */
@Service
@Scope("singleton")
public class NodeMonitorService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(NodeMonitorService.class);

	@Autowired
	private EthereumNetworkService ethereumNetworkService;

	@Autowired
	private LuniverseNetworkService luniverseNetworkService;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private AdminAlarmService adminAlarmService;

	BigInteger luniverseLastBlockNumber = null;

	// check every 10 minutes
	@Scheduled(fixedDelay = 1000 * 60 * 10, initialDelay = 1000)
	private void checkAll() {
		if(DexGovStatus.isStopped) return;

		try {
			checkEthereumNodes();
		} catch(Exception ex) {
			adminAlarmService.exception(logger, ex);
		}
		try {
			checkStellarNodes();
		} catch(Exception ex) {
			adminAlarmService.exception(logger, ex);
		}
		try {
			checkLuniverseNodes();
		} catch(Exception ex) {
			adminAlarmService.exception(logger, ex);
		}
	}

	private void checkEthereumNodes() throws IOException {
		ObjectPair<String, BigInteger> localInfo = getEthereumNodeInfo(ethereumNetworkService.getLocalClient());
		ObjectPair<String, BigInteger> infuraInfo = getEthereumNodeInfo(ethereumNetworkService.getInfuraClient());

		logger.info("Checking Ethereum node server status");
		logger.info("Local  : {} - {}", localInfo.second(), localInfo.first());
		logger.info("Infura : {} - {}", infuraInfo.second(), infuraInfo.first());

		long diff = infuraInfo.second().subtract(localInfo.second()).abs().longValueExact();

		if(diff >= 30) {
			adminAlarmService.warn(logger, "Local ethereum node is {} blocks behind than infura node.", diff);
		}
	}

	private ObjectPair<String, BigInteger> getEthereumNodeInfo(EthRpcClient client) throws IOException {
		String version = client.getClientVersion();
		BigInteger number = client.newClient().ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getNumber();
		return new ObjectPair<>(version, number);
	}

	private void checkStellarNodes() throws IOException {
		RootResponse localInfo = stellarNetworkService.pickServer().root();
		RootResponse publicInfo = stellarNetworkService.pickPublicServer().root();

		logger.info("Checking Stellar node server status");
		logger.info("Local  : {}/{} - {} / {}", localInfo.getHistoryLatestLedger(), localInfo.getCoreLatestLedger(), localInfo.getStellarCoreVersion(), localInfo.getHorizonVersion());
		logger.info("Public : {}/{} - {} / {}", publicInfo.getHistoryLatestLedger(), publicInfo.getCoreLatestLedger(), publicInfo.getStellarCoreVersion(), publicInfo.getHorizonVersion());

		int coreDiff = publicInfo.getCoreLatestLedger() - localInfo.getCoreLatestLedger();
		int localStale = Math.abs(localInfo.getCoreLatestLedger() - localInfo.getHistoryLatestLedger());

		if(coreDiff > 100) {
			adminAlarmService.warn(logger, "Local stellar node is {} ledgers behind than public node.", coreDiff);
		}

		if(localStale > 50) {
			adminAlarmService.warn(logger, "Local stellar node cannot ingest {} history ledgers. GAP check needed ASAP!", localStale);
		}

		if(publicInfo.getCoreSupportedProtocolVersion() != localInfo.getCoreSupportedProtocolVersion()) {
			adminAlarmService.warn(logger, "Local stellar node support protocol(={}, public={}) is old, may not able to perform correctly.", localInfo.getCoreSupportedProtocolVersion(), publicInfo.getCoreSupportedProtocolVersion());
		}
	}

	private void checkLuniverseNodes() throws IOException {
		Web3j web3j = luniverseNetworkService.newMainRpcClient();

		String version = web3j.web3ClientVersion().send().getWeb3ClientVersion();
		BigInteger number = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getNumber();

		logger.info("Checking Luniverse node server status");
		logger.info("Lambda256 : {} - {}", number, version);

		if(luniverseLastBlockNumber != null && number.compareTo(luniverseLastBlockNumber) == 0) {
			adminAlarmService.warn(logger, "Luniverse node might be stuck at {}, node is maintained by lambda256. ask for help.", number);
		}

		luniverseLastBlockNumber = number;
	}
}
