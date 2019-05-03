package io.talken.dex.shared.service;


import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.collection.ObjectPair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Scope("singleton")
public class EthereumNetworkService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumNetworkService.class);

	@Autowired
	private EthereumSetting ethereumSetting;

	private List<ObjectPair<String, Boolean>> serverList = new ArrayList<>();
	private SecureRandom random = new SecureRandom();

//	private static final int BASE_FEE = 100;

	@PostConstruct
	private void init() {
		if(ethereumSetting.getNetwork().equalsIgnoreCase("test")) {
			logger.info("Using Ethereum TEST Network.");
		} else {
			logger.info("Using Ethereum PUBLIC Network.");
		}
		for(String _s : ethereumSetting.getServerList()) {
			logger.info("Ethereum node {} added.", _s);
			serverList.add(new ObjectPair<>(_s, true));
		}
	}

	public Web3j newClient() throws IOException {
		List<String> availableServers = serverList.stream().filter(_sl -> _sl.second().equals(true)).map(ObjectPair::first).collect(Collectors.toList());
		Web3j web3j = Web3j.build(new HttpService(availableServers.get(random.nextInt(serverList.size()))));
		logger.debug("Connected to Ethereum client version: " + web3j.web3ClientVersion().send().getWeb3ClientVersion());
		return web3j;
	}
//
//	public Transaction.Builder getTransactionBuilderFor(TransactionBuilderAccount sourceAccount) {
//		return new Transaction.Builder(sourceAccount)
//				.setTimeout(Transaction.Builder.TIMEOUT_INFINITE)
//				.setOperationFee(getBaseFee());
//	}

//	public int getBaseFee() {
//		return BASE_FEE;
//	}
}
