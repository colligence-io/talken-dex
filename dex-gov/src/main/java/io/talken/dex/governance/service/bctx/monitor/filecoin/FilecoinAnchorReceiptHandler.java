package io.talken.dex.governance.service.bctx.monitor.filecoin;


import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.util.PrefixedLogger;
import org.jooq.Condition;
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
public class FilecoinAnchorReceiptHandler extends AbstractFilecoinAnchorReceiptHandler {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(FilecoinAnchorReceiptHandler.class);

	@Autowired
	private FilecoinTxMonitor txMonitor;

    /**
     * Instantiates a new Filecoin anchor receipt handler.
     */
    public FilecoinAnchorReceiptHandler() {
		super(logger);
		addBcType(BlockChainPlatformEnum.FILECOIN);
//		addBcType(BlockChainPlatformEnum.ETHEREUM_ERC20_TOKEN);
	}

	@PostConstruct
	private void init() {
		txMonitor.addReceiptHandler(this);
	}

	@Override
	protected Condition getBcTypeCondition(String contractAddr) {
//		if(contractAddr == null) {
			return DEX_TASK_ANCHOR.BCTX_TYPE.eq(BlockChainPlatformEnum.FILECOIN);
//		} else {
//			return DEX_TASK_ANCHOR.BCTX_TYPE.eq(BlockChainPlatformEnum.ETHEREUM_ERC20_TOKEN).and(DEX_TASK_ANCHOR.VC4S_PLATFORM_AUX.eq(contractAddr.toLowerCase()));
//		}
	}
}
