package io.talken.dex.governance.service.bctx;


import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.records.BctxLogRecord;
import io.talken.common.util.collection.SingleKeyObject;
import io.talken.dex.governance.service.TokenMeta;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.governance.service.integration.signer.SignServerService;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class TxSender implements SingleKeyObject<BlockChainPlatformEnum> {
	private BlockChainPlatformEnum platform;

	@Autowired
	private TokenMetaGovService tmService;

	@Autowired
	private SignServerService signServerService;

	public TxSender(BlockChainPlatformEnum platform) {
		this.platform = platform;
	}

	@Override
	public BlockChainPlatformEnum __getSKey__() {
		return getPlatform();
	}

	public BlockChainPlatformEnum getPlatform() {
		return this.platform;
	}

	public boolean buildAndSendTx(Bctx bctx, BctxLogRecord logRecord) throws Exception {
		TokenMeta meta = tmService.getMeta(bctx.getSymbol());

//		if(!meta.getPlatform().equals(bctx.getPlatform()))
//			throw new BctxException("PlatformNotMatch", "platform not match");

		return sendTx(meta, bctx, logRecord);
	}

	public abstract boolean sendTx(TokenMeta meta, Bctx bctx, BctxLogRecord log) throws Exception;

	protected SignServerService signServer() {
		return signServerService;
	}
}
