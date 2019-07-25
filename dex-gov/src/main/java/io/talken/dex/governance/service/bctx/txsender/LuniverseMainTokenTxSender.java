package io.talken.dex.governance.service.bctx.txsender;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.util.PrefixedLogger;
import org.springframework.stereotype.Component;

@Component
public class LuniverseMainTokenTxSender extends AbstractLuniverseTxSender {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(LuniverseMainTokenTxSender.class);

	public LuniverseMainTokenTxSender() {
		super(BlockChainPlatformEnum.LUNIVERSE_MAIN_TOKEN, logger);
	}
}