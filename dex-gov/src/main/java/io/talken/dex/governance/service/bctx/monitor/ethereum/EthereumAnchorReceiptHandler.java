package io.talken.dex.governance.service.bctx.monitor.ethereum;


import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.util.PrefixedLogger;
import org.jooq.Condition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_ANCHOR;

@Service
@Scope("singleton")
public class EthereumAnchorReceiptHandler extends AbstractEthereumAnchorReceiptHandler {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumAnchorReceiptHandler.class);

	@Autowired
	private EthereumTxMonitor txMonitor;

	public EthereumAnchorReceiptHandler() {
		super(logger);
	}

	@PostConstruct
	private void init() {
		txMonitor.addReceiptHandler(this);
	}

	@Override
	protected Condition getBcTypeCondition(String contractAddr) {
		if(contractAddr == null) {
			return DEX_TASK_ANCHOR.BCTX_TYPE.eq(BlockChainPlatformEnum.ETHEREUM);
		} else {
			return DEX_TASK_ANCHOR.BCTX_TYPE.eq(BlockChainPlatformEnum.ETHEREUM_ERC20_TOKEN).and(DEX_TASK_ANCHOR.PLATFORM_AUX.eq(contractAddr));
		}
	}
}