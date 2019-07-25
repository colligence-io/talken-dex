package io.talken.dex.governance.service.bctx.txsender;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.records.BctxLogRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.TokenMeta;
import org.springframework.stereotype.Component;

@Component
public class StellarTokenTxSender extends AbstractStellarTxSender {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(StellarTokenTxSender.class);

	public StellarTokenTxSender() {
		super(BlockChainPlatformEnum.STELLAR_TOKEN, logger);
	}

	@Override
	public boolean sendTx(TokenMeta meta, Bctx bctx, BctxLogRecord log) throws Exception {
		return sendStellarTx(meta.getManagedInfo().getAssetType(), bctx, log);
	}
}
