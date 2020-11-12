package io.talken.dex.governance.service.bctx;


import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.records.BctxLogRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.collection.SingleKeyObject;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.service.integration.signer.SignServerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * TxSender abstraction
 * NOTE : TxSender will be called in one singleton BlockChainTransactionService
 * this means TxSender of each type will be executed in one linear queue, not separated queue
 */
public abstract class TxSender implements SingleKeyObject<BlockChainPlatformEnum> {
    public enum ErrorCode {
        NO_TX_SENDER,
        CONTRACT_ID_NOT_MATCH;
    }

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
		TokenMetaTable.Meta meta = getTokenMeta(bctx.getSymbol());

//		if(!meta.getPlatform().equals(bctx.getPlatform()))
//			throw new BctxException("PlatformNotMatch", "platform not match");

		return sendTx(meta, bctx, logRecord);
	}

	public abstract boolean sendTx(TokenMetaTable.Meta meta, Bctx bctx, BctxLogRecord log) throws Exception;

	protected SignServerService signServer() {
		return signServerService;
	}

    protected BigInteger getEthAmount(Integer decimals, Bctx bctx) {
        BigInteger amount;

        if(decimals != null) {
            amount = bctx.getAmount().multiply(BigDecimal.TEN.pow(decimals)).toBigInteger();
        } else {
            amount = Convert.toWei(bctx.getAmount(), Convert.Unit.ETHER).toBigInteger();
        }

        return amount;
    }

    public TokenMetaTable.Meta getTokenMeta(String symbol) throws TokenMetaNotFoundException {
        return tmService.getTokenMeta(symbol);
    }

    public void setBctxLogFailedNoTxSender(PrefixedLogger logger, BctxLogRecord log, Bctx bctx, ErrorCode errorCode, String errorMessage) {
        logger.error("{} [BCTX#{}] / {}", errorCode, bctx.getId(), bctx.getBcRefId());
        log.setStatus(BctxStatusEnum.FAILED);
        log.setErrorcode(errorCode.name());
        log.setErrormessage(errorMessage);
    }
}
