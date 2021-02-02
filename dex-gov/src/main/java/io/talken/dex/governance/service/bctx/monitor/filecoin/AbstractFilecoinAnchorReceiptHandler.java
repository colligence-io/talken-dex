package io.talken.dex.governance.service.bctx.monitor.filecoin;

import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.records.BctxRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskAnchorRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.governance.service.bctx.TxMonitor;
import io.talken.dex.governance.service.bctx.monitor.AbstractAnchorReceiptHandler;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.TransactionBlockExecutor;
import io.talken.dex.shared.service.blockchain.filecoin.FilecoinMessage;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.math.BigDecimal;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_ANCHOR;

public abstract class AbstractFilecoinAnchorReceiptHandler extends AbstractAnchorReceiptHandler implements TxMonitor.ReceiptHandler<FilecoinMessage.Block, FilecoinMessage.SecpkMessage, FilecoinMessage.SecpkMessage> {
	private PrefixedLogger logger;

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private TokenMetaGovService tmService;

	@Autowired
	private DataSourceTransactionManager txMgr;

	public AbstractFilecoinAnchorReceiptHandler(PrefixedLogger logger) {
		this.logger = logger;
	}

	abstract protected Condition getBcTypeCondition(String contractAddr);

	/**
	 * handle receipt
	 * 1. check this receipt is anchoring (transfer to holder?)
	 * 2. check this receipt is anchoring (search DB task)
	 * 3. insert asset issueing bctx
	 *
	 * @param block
	 * @param txResult
	 * @param msg
	 * @throws Exception
	 */
	@Override
	public void handle(FilecoinMessage.Block block, FilecoinMessage.SecpkMessage txResult, FilecoinMessage.SecpkMessage msg) throws Exception {
		FilecoinMessage.Message receipt = msg.getMessage();
		// check transfer is to holder
		if (!checkHolder(receipt.getTo())) return;

		// convert amount to actual
		BigDecimal amountValue = receipt.getValue();
		BigDecimal amount;
//		if (receipt.getTokenDecimal() != null) {
//			amount = amountValue.divide(BigDecimal.TEN.pow(Integer.valueOf(receipt.getTokenDecimal())), 7, RoundingMode.FLOOR);
//		} else {
//			amount = Convert.fromWei(amountValue.toString(), Convert.Unit.ETHER);
//		}
		amount = StellarConverter.scale(amountValue);

		logger.info("Transfer to holder detected : {} -> {} : {} {}", receipt.getFrom(), receipt.getTo(), amount, BlockChainPlatformEnum.FILECOIN);

		// return amount is smaller than zero
		if (amount.compareTo(BigDecimal.ZERO) <= 0) return;

		Condition condition = DEX_TASK_ANCHOR.BC_REF_ID.isNull()
				.and(DEX_TASK_ANCHOR.VC4S_PRIVATEADDR.eq(receipt.getFrom().toLowerCase())
						.and(DEX_TASK_ANCHOR.VC4S_HOLDERADDR.eq(receipt.getTo().toLowerCase()))
						.and(DEX_TASK_ANCHOR.AMOUNT.eq(amount)));
//				.and(getBcTypeCondition(receipt.getContractAddress()));

		DexTaskAnchorRecord taskRecord = dslContext.selectFrom(DEX_TASK_ANCHOR)
				.where(condition)
				.orderBy(DEX_TASK_ANCHOR.CREATE_TIMESTAMP.desc()) // older request first
				.limit(1)
				.fetchOne();

		// finish if task not found
		if (taskRecord == null) {
			logger.error("Transfer to holder detected but no matching anchor task found : {} -> {} : {} {}", receipt.getFrom(), receipt.getTo(), amount, BlockChainPlatformEnum.FILECOIN);
			return;
		} else {
			logger.info("Transfer to holder detected for {} : {} -> {} : {} {}", taskRecord.getTaskid(), receipt.getFrom(), receipt.getTo(), amount, BlockChainPlatformEnum.FILECOIN);
		}

		taskRecord.setBcRefId(msg.getCid().getRoot());

		TokenMetaTable.ManagedInfo tm = tmService.getManagedInfo(taskRecord.getAssetcode());

		BctxRecord bctxRecord = new BctxRecord();
		bctxRecord.setBctxType(BlockChainPlatformEnum.STELLAR_TOKEN);
		bctxRecord.setSymbol(taskRecord.getAssetcode());
		bctxRecord.setPlatformAux(tm.getIssuerAddress());
		bctxRecord.setAddressFrom(tm.getIssuerAddress());
		bctxRecord.setAddressTo(taskRecord.getTradeaddr());
		bctxRecord.setAmount(amount);
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
