package io.talken.dex.governance.service.bctx.txsender;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.util.PrefixedLogger;
import org.springframework.stereotype.Component;

@Component
public class EthereumTxSender extends AbstractEthereumTxSender {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumTxSender.class);

	public EthereumTxSender() {
		super(BlockChainPlatformEnum.ETHEREUM, logger);
	}
}
