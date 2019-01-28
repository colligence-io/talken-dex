package io.colligence.talken.dex.service.integration.stellar;


import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.colligence.talken.common.util.JSONWriter;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.common.util.collection.ObjectPair;
import io.colligence.talken.dex.DexSettings;
import io.colligence.talken.dex.api.dex.TxSubmitResult;
import io.colligence.talken.dex.exception.TransactionHashNotMatchException;
import io.colligence.talken.dex.service.integration.APIResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Network;
import org.stellar.sdk.Server;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import javax.annotation.PostConstruct;
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

	public APIResult<TxSubmitResult> submitTx(String taskID, String txHash, String txEnvelopeXdr) throws TransactionHashNotMatchException {
		APIResult<TxSubmitResult> result = new APIResult<>("StellarSubmit");
		Transaction tx;
		try {
			tx = Transaction.fromEnvelopeXdr(txEnvelopeXdr);
		} catch(Exception ex) {
			result.setException(ex);
			return result;
		}

		// TODO : check taskID (txMemo? maybe?)
		if(!txHash.equalsIgnoreCase(ByteArrayUtil.toHexString(tx.hash()))) {
			throw new TransactionHashNotMatchException(txHash);
		}

		try {
			Server server = pickServer();

			SubmitTransactionResponse response = server.submitTransaction(tx);
			result.setData(new TxSubmitResult(response));

			if(response.isSuccess()) {
				result.setSuccess(true);
			} else {
				SubmitTransactionResponse.Extras extras = response.getExtras();
				result.setError(extras.getResultCodes().getTransactionResultCode(), JSONWriter.toJsonStringSafe(extras.getResultCodes().getOperationsResultCodes()));
			}
		} catch(Exception e) {
			result.setException(e);
		}
		return result;
	}
}
