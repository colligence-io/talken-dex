package io.colligence.talken.dex.service;


import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.common.util.collection.ObjectPair;
import io.colligence.talken.dex.DexSettings;
import io.colligence.talken.dex.api.dex.TxSubmitResult;
import io.colligence.talken.dex.exception.APICallException;
import io.colligence.talken.dex.exception.TransactionHashNotMatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Network;
import org.stellar.sdk.Server;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Scope("singleton")
public class StellarNetworkService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(StellarNetworkService.class);

	@Autowired
	private DexSettings dexSettings;

	private List<ObjectPair<String, Boolean>> serverList = new ArrayList<>();
	private SecureRandom random = new SecureRandom();

	@PostConstruct
	private void init() {
		if(dexSettings.getStellar().getNetwork().equalsIgnoreCase("test")) {
			logger.info("Using Stellar TEST Network.");
			Network.useTestNetwork();
		} else {
			logger.info("Using Stellar PUBLIC Network.");
			Network.usePublicNetwork();
		}
		for(String _s : dexSettings.getStellar().getServerList()) {
			logger.info("Horizon {} added.", _s);
			serverList.add(new ObjectPair<>(_s, true));
		}
	}

	public Server pickServer() {
		List<String> availableServers = serverList.stream().filter(_sl -> _sl.second().equals(true)).map(ObjectPair::first).collect(Collectors.toList());
		return new Server(availableServers.get(random.nextInt(serverList.size())));
	}

	public TxSubmitResult submitTx(String taskID, String txHash, String txEnvelopeXdr) throws TransactionHashNotMatchException, APICallException {
		try {
			Server server = pickServer();
			// TODO : check taskID & txHash is matched other throw TASK_INTEGRATION_FAIL

			Transaction tx = Transaction.fromEnvelopeXdr(txEnvelopeXdr);
			if(!txHash.equalsIgnoreCase(ByteArrayUtil.toHexString(tx.hash()))) {
				throw new TransactionHashNotMatchException(txHash);
			}

			SubmitTransactionResponse response = server.submitTransaction(tx);

			return new TxSubmitResult(response);
		} catch(IOException ioex) {
			throw new APICallException(ioex, "Stellar");
		}
	}
}
