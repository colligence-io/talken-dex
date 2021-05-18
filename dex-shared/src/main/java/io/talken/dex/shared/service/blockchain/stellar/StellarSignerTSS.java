package io.talken.dex.shared.service.blockchain.stellar;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.shared.exception.SigningException;
import io.talken.dex.shared.service.integration.signer.SignServerService;
import org.stellar.sdk.Transaction;

/**
 * Stellar Signer for TSS
 */
public class StellarSignerTSS implements StellarSigner {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(StellarSignerTSS.class);

	private SignServerService tss;
	private String accountId;

    /**
     * Instantiates a new Stellar signer tss.
     *
     * @param tss       the tss
     * @param accountId the account id
     */
    public StellarSignerTSS(SignServerService tss, String accountId) {
		this.tss = tss;
		this.accountId = accountId;
	}

	@Override
	public String __getSKey__() {
		return "TSS:" + this.accountId;
	}

	@Override
	public void sign(Transaction tx) throws SigningException {
		if(tss == null) throw new SigningException("Null", "TSS is null");
		if(accountId == null) throw new SigningException("Null", "TSS account is null");
		if(tx == null) throw new SigningException(accountId, "Tx is null");
		logger.debug("Request sign for {} {}", accountId, ByteArrayUtil.toHexString(tx.hash()));
		tss.signStellarTransaction(tx, accountId);
	}
}