package io.talken.dex.governance.service.bctx.monitor.stellar;

import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.records.BctxRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskAnchorRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.governance.service.bctx.TxMonitor;
import io.talken.dex.governance.service.bctx.monitor.AbstractAnchorReceiptHandler;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.TransactionBlockExecutor;
import io.talken.dex.shared.service.blockchain.stellar.StellarOpReceipt;
import io.talken.dex.shared.service.blockchain.stellar.StellarTxReceipt;
import io.talken.dex.shared.service.blockchain.stellar.opreceipt.PaymentOpReceipt;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_ANCHOR;

@Service
@Scope("singleton")
public class StellarAnchorReceiptHandler extends AbstractAnchorReceiptHandler implements TxMonitor.ReceiptHandler<Void, StellarTxReceipt, StellarOpReceipt> {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(StellarAnchorReceiptHandler.class);

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private TokenMetaGovService tmService;

	@Autowired
	private DataSourceTransactionManager txMgr;

	@Autowired
	private StellarTxMonitor txMonitor;

	public StellarAnchorReceiptHandler() {
		addBcType(BlockChainPlatformEnum.STELLAR);
		addBcType(BlockChainPlatformEnum.STELLAR_TOKEN);
	}

	@PostConstruct
	private void init() {
		txMonitor.addReceiptHandler(this);
	}

	@Override
	public void handle(Void _void, StellarTxReceipt txResult, StellarOpReceipt receipt) throws Exception {

		if(!(receipt instanceof PaymentOpReceipt)) {
			logger.error("Cannot handle {}", receipt.getOperationType());
			return;
		}

		PaymentOpReceipt opReceipt = (PaymentOpReceipt) receipt;

		// check transfer is to holder
		if(!checkHolder(opReceipt.getTo())) return;

		Condition condition = DEX_TASK_ANCHOR.BC_REF_ID.isNull()
				.and(DEX_TASK_ANCHOR.PRIVATEADDR.eq(opReceipt.getFrom()).and(DEX_TASK_ANCHOR.HOLDERADDR.eq(opReceipt.getTo())).and(DEX_TASK_ANCHOR.AMOUNT.eq(opReceipt.getAmount())));

		if(opReceipt.getAssetIssuer() == null) {
			condition = condition.and(DEX_TASK_ANCHOR.BCTX_TYPE.eq(BlockChainPlatformEnum.STELLAR));
		} else {
			condition = condition.and(DEX_TASK_ANCHOR.BCTX_TYPE.eq(BlockChainPlatformEnum.STELLAR_TOKEN).and(DEX_TASK_ANCHOR.PLATFORM_AUX.eq(opReceipt.getAssetIssuer())));
		}

		DexTaskAnchorRecord taskRecord = dslContext.selectFrom(DEX_TASK_ANCHOR)
				.where(condition)
				.orderBy(DEX_TASK_ANCHOR.CREATE_TIMESTAMP.desc()) // older request first
				.limit(1)
				.fetchOne();

		// finish if task not found
		if(taskRecord == null) {
			logger.error("Transfer to holder detected but no matching anchor task found : [{}] {} -> {} : {} {}({})", receipt.getHash(), opReceipt.getFrom(), opReceipt.getTo(), opReceipt.getAmount(), opReceipt.getAssetCode(), opReceipt.getAssetIssuer());
			return;
		} else {
			logger.info("Transfer to holder detected : {} -> {} : {} {}({})", opReceipt.getFrom(), opReceipt.getTo(), opReceipt.getAmount(), opReceipt.getAssetCode(), opReceipt.getAssetIssuer());
		}

		taskRecord.setBcRefId(receipt.getHash());
		taskRecord.update();

		TokenMetaTable.ManagedInfo tm = tmService.getManagedInfo(taskRecord.getAssetcode());

		BctxRecord bctxRecord = new BctxRecord();
		bctxRecord.setStatus(BctxStatusEnum.QUEUED);
		bctxRecord.setBctxType(BlockChainPlatformEnum.STELLAR_TOKEN);
		bctxRecord.setSymbol(taskRecord.getAssetcode());
		bctxRecord.setPlatformAux(tm.getIssuerAddress());
		bctxRecord.setAddressFrom(tm.getIssuerAddress());
		bctxRecord.setAddressTo(taskRecord.getTradeaddr());
		bctxRecord.setAmount(opReceipt.getAmount());
		bctxRecord.setNetfee(BigDecimal.ZERO);
		bctxRecord.setTxAux(taskRecord.getTaskid());
		dslContext.attach(bctxRecord);

		TransactionBlockExecutor.of(txMgr).transactional(() -> {
			bctxRecord.store();
			taskRecord.setIssueBctxId(bctxRecord.getId());
			logger.info("Anchor task {} issuing bctx queued : #{}", taskRecord.getTaskid(), bctxRecord.getId());
			taskRecord.store();
		});
	}
}
