package io.colligence.talken.dex.service.integration.signer;

import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.DexSettings;
import io.colligence.talken.dex.exception.SigningException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.Transaction;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Service
@Scope("singleton")
public class SignerService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(SignerService.class);

	@Autowired
	private DexSettings dexSettings;

	private Map<String, KeyPair> kps = new HashMap<>();

	@PostConstruct
	private void init() {
		Map<String, String> signerMock = dexSettings.getSignerMock();
		for(Map.Entry<String, String> _kp : signerMock.entrySet()) {
			kps.put(_kp.getKey(), KeyPair.fromSecretSeed(_kp.getValue()));
		}
	}

	public void sign(Transaction tx) throws SigningException {
		if(kps.containsKey(tx.getSourceAccount().getAccountId())) {
			tx.sign(kps.get(tx.getSourceAccount().getAccountId()));
		} else throw new SigningException(tx.getSourceAccount().getAccountId(), "Signer not found");
	}
}
