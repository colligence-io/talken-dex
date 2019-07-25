package io.talken.dex.governance.service.bctx;


import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.records.BctxLogRecord;
import io.talken.common.persistence.jooq.tables.records.BctxRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.SingleKeyTable;
import io.talken.dex.shared.TransactionBlockExecutor;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;

import static io.talken.common.persistence.jooq.Tables.BCTX;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class BlockChainTransactionService implements ApplicationContextAware {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(BlockChainTransactionService.class);

	// constructor autowire with lomkok
	private final DSLContext dslContext;

	// constructor autowire with lomkok
	private final DataSourceTransactionManager txMgr;

	private final SingleKeyTable<BlockChainPlatformEnum, TxSender> txSenders = new SingleKeyTable<>();

	private ApplicationContext applicationContext;

	private final static int RETRY_INTERVAL = 300;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@PostConstruct
	private void init() {
		Map<String, TxSender> txsBeans = applicationContext.getBeansOfType(TxSender.class);
		txsBeans.forEach((_name, _bean) -> {
			txSenders.insert(_bean);
			logger.info("TxSender for [{}] registered.", _bean.getPlatform());
		});

		Map<String, TxMonitor> txmBeans = applicationContext.getBeansOfType(TxMonitor.class);
		txmBeans.forEach((_name, _bean) -> {
			logger.info("TxMonitor [{}] loaded.", _bean.getClass().getSimpleName());
		});
	}

	@Scheduled(fixedDelay = 1000, initialDelay = 3000)
	private void checkQueue() {
		Result<BctxRecord> txQueue = dslContext.selectFrom(BCTX)
				.where(BCTX.STATUS.eq(BctxStatusEnum.QUEUED)
						.and(BCTX.SCHEDULE_TIMESTAMP.isNull().or(BCTX.SCHEDULE_TIMESTAMP.ne(UTCUtil.getNow())))
				).fetch();

		for(BctxRecord bctxRecord : txQueue) {

			// create new logRecord
			BctxLogRecord logRecord = new BctxLogRecord();
			try {
				bctxRecord.setStatus(BctxStatusEnum.SENT);
				logRecord.setBctxId(bctxRecord.getId());
				dslContext.attach(logRecord);

				// mark as sent for failover safety, prevent sending repeatly
				TransactionBlockExecutor.of(txMgr).transactional(() -> {
					bctxRecord.update();
					logRecord.store();
				});
			} catch(Exception ex) {
				logger.exception(ex, "Cannot create new bctx_log record. bctx canceled.");
				break;
			}

			try {
				if(txSenders.has(bctxRecord.getBctxType())) {
					boolean successful = txSenders.select(bctxRecord.getBctxType()).buildAndSendTx(bctxRecord.into(Bctx.class), logRecord);
					if(successful) {
						logRecord.setStatus(BctxStatusEnum.SENT);
					} else {
						logRecord.setStatus(BctxStatusEnum.FAILED);
					}
				} else {
					logRecord.setStatus(BctxStatusEnum.FAILED);
					logRecord.setErrorcode("NoTxSender");
					logRecord.setErrormessage("TxSender " + bctxRecord.getBctxType() + " not found");
				}
			} catch(Exception ex) {
				logRecord.setStatus(BctxStatusEnum.FAILED);
				logRecord.setErrorcode(ex.getClass().getSimpleName());
				logRecord.setErrormessage(ex.getMessage());
			}

			try {
				if(logRecord.getStatus().equals(BctxStatusEnum.SENT)) {
					logger.info("[BCTX#{}] BcTx Successfully sent", bctxRecord.getId());
					bctxRecord.setStatus(BctxStatusEnum.SENT);
					bctxRecord.setBcRefId(logRecord.getBcRefId());
				} else {
					logger.info("[BCTX#{}] BcTx Failed, {} : {}", bctxRecord.getId(), logRecord.getErrorcode(), logRecord.getErrormessage());
					bctxRecord.setStatus(BctxStatusEnum.FAILED);
					// FIXME : AUTORETRY DISABLED FOR SAFETY
//					bctxRecord.setScheduleTimestamp(UTCUtil.getNow().plusSeconds(RETRY_INTERVAL));
				}

				TransactionBlockExecutor.of(txMgr).transactional(() -> {
					bctxRecord.update();
					logRecord.store();
				});
			} catch(Exception ex) {
				logger.exception(ex);
			}
		}
	}
}
