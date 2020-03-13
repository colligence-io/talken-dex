package io.talken.dex.governance.service.bctx.monitor.luniverse;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.bctx.monitor.ethereum.AbstractEthereumAnchorReceiptHandler;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_ANCHOR;

/**
 * TxMonitor.ReceiptHandler for anchoring
 */
@Service
@Scope("singleton")
public class LuniverseAnchorReceiptHandler extends AbstractEthereumAnchorReceiptHandler {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(LuniverseAnchorReceiptHandler.class);

	@Autowired
	private LuniverseTxMonitor txMonitor;

	public LuniverseAnchorReceiptHandler() {
		super(logger);
		addBcType(BlockChainPlatformEnum.LUNIVERSE);
		addBcType(BlockChainPlatformEnum.LUNIVERSE_MAIN_TOKEN);
	}

	@PostConstruct
	private void init() {
		// disabled temporary
		// txMonitor.addReceiptHandler(this);
	}

	@Override
	protected Condition getBcTypeCondition(String contractAddr) {
		if(contractAddr == null) {
			return DEX_TASK_ANCHOR.BCTX_TYPE.eq(BlockChainPlatformEnum.LUNIVERSE);
		} else {
			return DEX_TASK_ANCHOR.BCTX_TYPE.eq(BlockChainPlatformEnum.LUNIVERSE_MAIN_TOKEN).and(DEX_TASK_ANCHOR.VC4S_PLATFORM_AUX.eq(contractAddr.toLowerCase()));
		}
	}
}