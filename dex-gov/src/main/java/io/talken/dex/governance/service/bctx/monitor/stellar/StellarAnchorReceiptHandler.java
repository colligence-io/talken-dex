package io.talken.dex.governance.service.bctx.monitor.stellar;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.bctx.TxMonitor;
import io.talken.dex.shared.service.blockchain.stellar.StellarTxReceipt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
@Scope("singleton")
public class StellarAnchorReceiptHandler implements TxMonitor.ReceiptHandler<StellarTxReceipt> {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(StellarAnchorReceiptHandler.class);

	@Autowired
	private StellarTxMonitor txMonitor;

	@PostConstruct
	private void init() {
		txMonitor.addReceiptHandler(this);
	}

	@Override
	public void handle(StellarTxReceipt receipt) throws Exception {
//		logger.logObjectAsJSON(receipt);
	}
}
