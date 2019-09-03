package io.talken.dex.governance.service.bctx;

import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.jooq.tables.records.BctxLogRecord;
import io.talken.common.util.GSONWriter;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.shared.TransactionBlockExecutor;
import io.talken.dex.shared.exception.BctxException;
import lombok.Data;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.util.ArrayList;
import java.util.List;

import static io.talken.common.persistence.jooq.Tables.BCTX;
import static io.talken.common.persistence.jooq.Tables.BCTX_LOG;

public abstract class TxMonitor<TB, TT> {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TxMonitor.class);

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private DataSourceTransactionManager txMgr;

	@Autowired
	private AdminAlarmService alarmService;

	private List<BlockHandler<TB>> blockHandlers = new ArrayList<>();
	private List<TransactionHandler<TT>> txHandlers = new ArrayList<>();

	public void addBlockHandler(BlockHandler<TB> blockHandler) {
		logger.info("{} Block -> {} binded.", this.getClass().getSimpleName(), blockHandler.getClass().getSimpleName());
		this.blockHandlers.add(blockHandler);
	}

	public void addTransactionHandler(TransactionHandler<TT> transactionHandler) {
		logger.info("{} Transaction -> {} binded.", this.getClass().getSimpleName(), transactionHandler.getClass().getSimpleName());
		this.txHandlers.add(transactionHandler);
	}

	protected void callBlockHandlerStack(TB block) throws BctxException {
		for(BlockHandler<TB> blockHandler : blockHandlers) {
			try {
				blockHandler.handle(block);
			} catch(Exception ex) {
				alarmService.exception(logger, ex);
				throw new BctxException(ex, ex.getClass().getSimpleName(), ex.getMessage());
			}
		}
	}

	protected void callTxHandlerStack(TT tx) throws Exception {
		updateBctxReceiptInfo(tx);
		for(TransactionHandler<TT> txHandler : txHandlers) {
			try {
				txHandler.handle(tx);
			} catch(Exception ex) {
				alarmService.exception(logger, ex);
				throw new BctxException(ex, ex.getClass().getSimpleName(), ex.getMessage());
			}
		}
	}

	abstract protected TxReceipt toTxMonitorReceipt(TT tx);

	private void updateBctxReceiptInfo(TT tx) throws Exception {

		TxReceipt receipt = toTxMonitorReceipt(tx);

		BctxLogRecord logRecord = dslContext.selectFrom(BCTX_LOG)
				.where(BCTX_LOG.BC_REF_ID.eq(receipt.txRefId).and(BCTX_LOG.STATUS.eq(BctxStatusEnum.SENT)))
				.fetchOne();

		if(logRecord != null) {
			logger.info("TxReceipt of BCTX#{}[{}] arrived. {}, refId = {}", logRecord.getBctxId(), logRecord.getId(), receipt.status, receipt.getTxRefId());
			TransactionBlockExecutor.of(txMgr).transactional(() -> {
				logRecord.setStatus(receipt.status);
				logRecord.setTxReceipt(receipt.receipt);

				logRecord.update();

				dslContext.update(BCTX)
						.set(BCTX.STATUS, receipt.status)
						.where(BCTX.ID.eq(logRecord.getBctxId()))
						.execute();
			});
		}
	}

	public interface BlockHandler<TB> {
		void handle(TB block) throws Exception;
	}

	public interface TransactionHandler<TT> {
		void handle(TT transaction) throws Exception;
	}

	@Data
	public static class TxReceipt {
		private BctxStatusEnum status;
		private String txRefId;
		private String receipt;

		private TxReceipt() {}

		public static TxReceipt ofSuccessful(String txRefId, Object receiptObject) {
			TxReceipt rtn = new TxReceipt();
			rtn.status = BctxStatusEnum.SUCCESS;
			rtn.txRefId = txRefId;
			rtn.receipt = GSONWriter.toJsonStringSafe(receiptObject);
			return rtn;
		}

		public static TxReceipt ofFailed(String txRefId, Object receiptObject) {
			TxReceipt rtn = new TxReceipt();
			rtn.status = BctxStatusEnum.FAILED;
			rtn.txRefId = txRefId;
			rtn.receipt = GSONWriter.toJsonStringSafe(receiptObject);
			return rtn;
		}
	}
}
