package io.talken.dex.governance.service.bctx.txsender;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.util.PrefixedLogger;
import org.springframework.stereotype.Component;

/**
 * The type Luniverse luk tx sender.
 */
@Component
public class LuniverseLukTxSender extends AbstractLuniverseTxSender {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(LuniverseLukTxSender.class);

    /**
     * Instantiates a new Luniverse luk tx sender.
     */
    public LuniverseLukTxSender() {
		super(BlockChainPlatformEnum.LUNIVERSE, logger);
	}
}
