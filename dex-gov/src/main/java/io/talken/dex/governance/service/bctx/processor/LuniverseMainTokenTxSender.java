package io.talken.dex.governance.service.bctx.processor;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.pojos.BctxLog;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.TokenMeta;
import org.springframework.stereotype.Component;

@Component
public class LuniverseMainTokenTxSender extends AbstractLuniverseTxSender {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(LuniverseMainTokenTxSender.class);

	public LuniverseMainTokenTxSender() {
		super(BlockChainPlatformEnum.LUNIVERSE_MAIN_TOKEN, logger);
	}
}