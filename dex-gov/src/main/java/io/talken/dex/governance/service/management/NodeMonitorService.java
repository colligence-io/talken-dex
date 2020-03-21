package io.talken.dex.governance.service.management;


import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
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
import java.time.LocalDate;

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

	LocalDate lastAnnounce = null;

	// check every 10 minutes
	@Scheduled(fixedDelay = 1000 * 60 * 10, initialDelay = 1000)
	private void checkAll() {
		if(DexGovStatus.isStopped) return;

		StringBuilder sb = new StringBuilder("Node Service Status\n");

		try {
			checkEthereumNodes(sb);
		} catch(Exception ex) {
			adminAlarmService.exception(logger, ex);
		}
		try {
			checkStellarNodes(sb);
		} catch(Exception ex) {
			adminAlarmService.exception(logger, ex);
		}
		try {
			checkLuniverseNodes(sb);
		} catch(Exception ex) {
			adminAlarmService.exception(logger, ex);
		}

		LocalDate today = UTCUtil.getNow().toLocalDate();
		if(lastAnnounce == null || today.compareTo(lastAnnounce) != 0) {
			adminAlarmService.info(logger, sb.toString());
		}
		lastAnnounce = today;
	}

	private void checkEthereumNodes(StringBuilder sb) throws IOException {
		ObjectPair<String, BigInteger> localInfo = getEthereumNodeInfo(ethereumNetworkService.getLocalClient());
		ObjectPair<String, BigInteger> infuraInfo = getEthereumNodeInfo(ethereumNetworkService.getInfuraClient());

		sb.append("Ethereum node server status").append("\n");
		sb.append(" - Local  : ").append(localInfo.second()).append(" - ").append(localInfo.first()).append("\n");
		sb.append(" - Infura  : ").append(infuraInfo.second()).append(" - ").append(infuraInfo.first()).append("\n");
		sb.append("\n");

		long diff = infuraInfo.second().subtract(localInfo.second()).abs().longValueExact();

		if(diff >= 20) {
			adminAlarmService.warn(logger, "Local ethereum node is {} blocks behind from infura node.", diff);
		}
	}

	private ObjectPair<String, BigInteger> getEthereumNodeInfo(EthRpcClient client) throws IOException {
		String version = client.getClientVersion();
		BigInteger number = client.newClient().ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getNumber();
		return new ObjectPair<>(version, number);
	}

	private void checkStellarNodes(StringBuilder sb) throws IOException {
		RootResponse localInfo = stellarNetworkService.pickServer().root();
		RootResponse publicInfo = stellarNetworkService.pickPublicServer().root();

		sb.append("Stellar node server status").append("\n");
		sb.append(" - Local  : ").append(localInfo.getHistoryLatestLedger()).append("/").append(localInfo.getCoreLatestLedger()).append(" - ").append(normalizeStellarVersion(localInfo)).append("\n");
		sb.append(" - Public  : ").append(publicInfo.getHistoryLatestLedger()).append("/").append(publicInfo.getCoreLatestLedger()).append(" - ").append(normalizeStellarVersion(publicInfo)).append("\n");
		sb.append("\n");

		int coreDiff = publicInfo.getCoreLatestLedger() - localInfo.getCoreLatestLedger();
		int localStale = Math.abs(localInfo.getCoreLatestLedger() - localInfo.getHistoryLatestLedger());

		if(coreDiff > 50) {
			adminAlarmService.warn(logger, "Local stellar node is {} ledgers behind from public node.", coreDiff);
		}

		if(localStale > 20) {
			adminAlarmService.warn(logger, "Local stellar node cannot ingest {} history ledgers. GAP check needed ASAP!", localStale);
		}

		if(publicInfo.getCoreSupportedProtocolVersion() != localInfo.getCoreSupportedProtocolVersion()) {
			adminAlarmService.warn(logger, "Local stellar node support protocol(={}, public={}) is old, may not able to perform correctly.", localInfo.getCoreSupportedProtocolVersion(), publicInfo.getCoreSupportedProtocolVersion());
		}
	}

	private String normalizeStellarVersion(RootResponse root) {
		String coreVersion = root.getStellarCoreVersion()
				.replaceAll("^(v|stellar-core)", "")
				.replaceAll("\\([0-9abcdefABCDEF]{40}\\)", "")
				.trim();

		String horizonVersion = root.getHorizonVersion()
				.replaceAll("^(v)", "")
				.replaceAll("-.?[0-9abcdefABCDEF]{40}", "")
				.trim();

		return coreVersion + " - " + horizonVersion + " (SCP current: " + root.getCurrentProtocolVersion() + ", support: " + root.getCoreSupportedProtocolVersion() + ")";
	}

	private void checkLuniverseNodes(StringBuilder sb) throws IOException {
		Web3j web3j = luniverseNetworkService.newMainRpcClient();

		String version = web3j.web3ClientVersion().send().getWeb3ClientVersion();
		BigInteger number = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getNumber();

		sb.append("Luniverse node server status").append("\n");
		sb.append(" - Lambda256 : ").append(number).append(" - ").append(version).append("\n");
		sb.append("\n");

		if(luniverseLastBlockNumber != null && number.compareTo(luniverseLastBlockNumber) == 0) {
			adminAlarmService.warn(logger, "Luniverse node might be stuck at {}, node is maintained by lambda256. ask for help.", number);
		}

		luniverseLastBlockNumber = number;
	}
}
