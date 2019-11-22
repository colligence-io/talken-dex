package io.talken.dex.governance.service.bctx.monitor.luniverse;

import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.bctx.TxMonitor;
import io.talken.dex.shared.service.blockchain.ethereum.EthereumTxReceipt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
@Scope("singleton")
public class LuniverseAnchorReceiptHandler implements TxMonitor.ReceiptHandler<EthereumTxReceipt> {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(LuniverseAnchorReceiptHandler.class);

	@Autowired
	private LuniverseTxMonitor txMonitor;

	@PostConstruct
	private void init() {
		txMonitor.addReceiptHandler(this);
	}

	@Override
	public void handle(EthereumTxReceipt receipt) throws Exception {
//		logger.info("{} {} {}", receipt.getFrom(), receipt.getTo(), receipt.getValue());
	}
}
