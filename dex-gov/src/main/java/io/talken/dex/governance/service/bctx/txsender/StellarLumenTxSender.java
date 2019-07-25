package io.talken.dex.governance.service.bctx.txsender;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.pojos.BctxLog;
import io.talken.common.persistence.jooq.tables.records.BctxLogRecord;
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
	public boolean sendTx(TokenMeta meta, Bctx bctx, BctxLogRecord log) throws Exception {
		return sendStellarTx(new AssetTypeNative(), bctx, log);
	}
}
