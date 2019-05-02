package io.talken.dex.governance.service.bctx.processor;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.pojos.BctxLog;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.TokenMeta;
import org.springframework.stereotype.Component;
import org.stellar.sdk.AssetTypeNative;

@Component
public class StellarLumenTxSender extends AbstractStellarTxSender {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(StellarLumenTxSender.class);

	public StellarLumenTxSender() {
		super(BlockChainPlatformEnum.STELLAR, logger);
	}

	@Override
	public void sendTx(TokenMeta meta, Bctx bctx, BctxLog log) throws Exception {
		sendStellarTx(new AssetTypeNative(), bctx, log);
	}
}
