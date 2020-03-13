package io.talken.dex.governance.service.bctx;

import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.records.BctxLogRecord;
import io.talken.common.util.GSONWriter;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.shared.TransactionBlockExecutor;
import io.talken.dex.shared.exception.BctxException;
import lombok.Data;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static io.talken.common.persistence.jooq.Tables.BCTX;
import static io.talken.common.persistence.jooq.Tables.BCTX_LOG;

/**
 * TxMonitor abstraction for handling block, tx, receipt
 * NOTE : TxMonitor implementation class has it's own singleton bean not like TxSender
 * this means monitor beans has it's own schedule and not affected by other monitor
 *
 * @param <TB>
 * @param <TT>
 * @param <TR>
 */
public abstract class TxMonitor<TB, TT, TR> {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TxMonitor.class);

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private DataSourceTransactionManager txMgr;

	@Autowired
	private AdminAlarmService alarmService;

	private List<BlockHandler<TB>> blockHandlers = new ArrayList<>();
	private List<TransactionHandler<TB, TT>> txHandlers = new ArrayList<>();
	private List<ReceiptHandler<TB, TT, TR>> receiptHandlers = new ArrayList<>();

	private BigInteger lastBlockNumber = null;
	private LocalDateTime lastBlockUpdateTime = null;


	public interface BlockHandler<TB> {
		void handle(TB block) throws Exception;
	}

	public interface TransactionHandler<TB, TT> {
		void handle(TB block, TT transaction) throws Exception;
	}

	public interface ReceiptHandler<TB, TT, TR> {
		void handle(TB block, TT transaction, TR receipt) throws Exception;
	}

	/**
	 * return what platform this is monitoring
	 * pending check routine uses this method to find proper txMonitor for bctx record
	 *
	 * @return
	 */
	public abstract BlockChainPlatformEnum[] getBcTypes();

	public void addBlockHandler(BlockHandler<TB> blockHandler) {
		logger.info("BlockHandler {} binded on {}.", blockHandler.getClass().getSimpleName(), this.getClass().getSimpleName());
		this.blockHandlers.add(blockHandler);
	}

	public void addTransactionHandler(TransactionHandler<TB, TT> transactionHandler) {
		logger.info("TransactionHandler {} binded on {}", transactionHandler.getClass().getSimpleName(), this.getClass().getSimpleName());
		this.txHandlers.add(transactionHandler);
	}

	public void addReceiptHandler(ReceiptHandler<TB, TT, TR> receiptHandler) {
		logger.info("ReceiptHandler {} binded on {}", receiptHandler.getClass().getSimpleName(), this.getClass().getSimpleName());
		this.receiptHandlers.add(receiptHandler);
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

	protected void callTxHandlerStack(TB block, TT tx) throws Exception {
		// uncomment this to check receipt in realtime (very slow)
		//updateBctxReceiptInfo(tx);
		for(TransactionHandler<TB, TT> txHandler : txHandlers) {
			try {
				txHandler.handle(block, tx);
			} catch(Exception ex) {
				alarmService.exception(logger, ex);
				throw new BctxException(ex, ex.getClass().getSimpleName(), ex.getMessage());
			}
		}
	}

	protected void callReceiptHandlerStack(TB block, TT tx, TR receipt) throws Exception {
		for(ReceiptHandler<TB, TT, TR> receiptHandler : receiptHandlers) {
			try {
				receiptHandler.handle(block, tx, receipt);
			} catch(Exception ex) {
				alarmService.exception(logger, ex);
				throw new BctxException(ex, ex.getClass().getSimpleName(), ex.getMessage());
			}
		}
	}

	/**
	 * convert TT to TxReceipt
	 *
	 * @param tx
	 * @return
	 */
	abstract protected TxReceipt toTxMonitorReceipt(TT tx);

	/**
	 * get TT from txID
	 *
	 * @param txId
	 * @return
	 */
	abstract protected TT getTransactionReceipt(String txId);

	/**
	 * check txId's status from network and update bctxReceipt
	 * this is fail safe function for network timeout, unknown error and so on
	 * see BlockChainTrasactionService.checkPending()
	 *
	 * @param txId
	 * @throws Exception
	 */
	public void checkTransactionStatus(String txId) throws Exception {
		TT tx = getTransactionReceipt(txId);
		if(tx != null) updateBctxReceiptInfo(tx);
	}

	/**
	 * check local monitoring node server is stuck or stopped working
	 *
	 * @param newBlockNumber
	 */
	protected void checkChainNetworkNode(BigInteger newBlockNumber) {
		if(lastBlockUpdateTime != null) {
			// if paging token is not updating for 1000
			if(lastBlockNumber.equals(newBlockNumber)) {
				if(UTCUtil.getNow().isAfter(lastBlockUpdateTime.plusMinutes(10))) {
					alarmService.warn(logger, "{} stuck at txPagingToken {} for 10 minutes", getClass().getSimpleName(), lastBlockNumber);
				}
			} else {
				// if new paging token is smaller than last (this indicates stellar network reset)
				if(lastBlockNumber.compareTo(newBlockNumber) > 0) {
					alarmService.warn(logger, "{} response small txPagingToken {} check stellar-core", getClass().getSimpleName(), newBlockNumber);
				}
			}
		} else {
			logger.info("{} starts from {}", getClass().getSimpleName(), newBlockNumber);
		}
		lastBlockUpdateTime = UTCUtil.getNow();
		lastBlockNumber = newBlockNumber;
	}

	/**
	 * update bctx record with receipt info
	 *
	 * @param tx
	 * @throws Exception
	 */
	private void updateBctxReceiptInfo(TT tx) throws Exception {

		TxReceipt receipt = toTxMonitorReceipt(tx);

		BctxLogRecord logRecord = dslContext.selectFrom(BCTX_LOG)
				.where(BCTX_LOG.BC_REF_ID.eq(receipt.txRefId.toLowerCase()).and(BCTX_LOG.STATUS.eq(BctxStatusEnum.SENT)))
				.orderBy(BCTX_LOG.ID.desc())
				.limit(1)
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
