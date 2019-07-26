package io.talken.dex.governance.service.bctx.txsender;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.util.PrefixedLogger;
import org.springframework.stereotype.Component;

@Component
public class EthereumErc20TxSender extends AbstractEthereumTxSender {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(EthereumErc20TxSender.class);

	public EthereumErc20TxSender() {
		super(BlockChainPlatformEnum.ETHEREUM_ERC20_TOKEN, logger);
	}
}