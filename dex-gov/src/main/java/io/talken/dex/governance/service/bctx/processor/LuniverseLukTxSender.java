package io.talken.dex.governance.service.bctx.processor;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.util.PrefixedLogger;
import org.springframework.stereotype.Component;

@Component
public class LuniverseLukTxSender extends AbstractLuniverseTxSender {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(LuniverseLukTxSender.class);

	public LuniverseLukTxSender() {
		super(BlockChainPlatformEnum.LUNIVERSE, logger);
	}
}
