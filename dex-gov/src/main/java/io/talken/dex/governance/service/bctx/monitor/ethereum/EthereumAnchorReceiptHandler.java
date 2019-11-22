package io.talken.dex.governance.service.bctx.monitor.ethereum;


import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.bctx.TxMonitor;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumTxReceipt;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
@Scope("singleton")
public class EthereumAnchorReceiptHandler implements TxMonitor.ReceiptHandler<EthereumTxReceipt> {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumAnchorReceiptHandler.class);

	@Autowired
	private EthereumTxMonitor txMonitor;

	@Autowired
	private DSLContext dslContext;

	@PostConstruct
	private void init() {
		txMonitor.addReceiptHandler(this);
	}

	@Override
	public void handle(EthereumTxReceipt receipt) throws Exception {
//		logger.info("{} {} {}", receipt.getFrom(), receipt.getTo(), receipt.getValue());
	}
}
