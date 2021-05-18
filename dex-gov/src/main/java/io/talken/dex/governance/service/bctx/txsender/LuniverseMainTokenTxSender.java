package io.talken.dex.governance.service.bctx.txsender;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.util.PrefixedLogger;
import org.springframework.stereotype.Component;

/**
 * The type Luniverse main token tx sender.
 */
@Component
public class LuniverseMainTokenTxSender extends AbstractLuniverseTxSender {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(LuniverseMainTokenTxSender.class);

    /**
     * Instantiates a new Luniverse main token tx sender.
     */
    public LuniverseMainTokenTxSender() {
		super(BlockChainPlatformEnum.LUNIVERSE_MAIN_TOKEN, logger);
	}
}