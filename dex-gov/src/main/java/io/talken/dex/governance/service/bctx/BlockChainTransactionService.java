package io.talken.dex.governance.service.bctx;


import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.pojos.BctxLog;
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
			logger.debug("TxSender for [{}] registered.", _bean.getPlatform());
		});
	}

	@Scheduled(fixedDelay = 1000, initialDelay = 3000)
	private void checkQueue() {
		Result<BctxRecord> txQueue = dslContext.selectFrom(BCTX)
				.where(
						BCTX.STATUS.eq(BctxStatusEnum.QUEUED)
								.and(BCTX.SCHEDULE_TIMESTAMP.isNull().or(BCTX.SCHEDULE_TIMESTAMP.ne(UTCUtil.getNow())))
				)
				.fetch();


		for(BctxRecord bctxRecord : txQueue) {
			BctxLog sendLog;

			try {
				if(txSenders.has(bctxRecord.getBctxType())) {
					sendLog = txSenders.select(bctxRecord.getBctxType()).buildAndSendTx(bctxRecord.into(Bctx.class));
					if(sendLog == null) {
						sendLog = new BctxLog();
						sendLog.setSuccessFlag(false);
						sendLog.setErrorcode("NullResult");
						sendLog.setErrormessage("TxSender returned null");
					}
				} else {
					sendLog = new BctxLog();
					sendLog.setSuccessFlag(false);
					sendLog.setErrorcode("NoTxSender");
					sendLog.setErrormessage("TxSender " + bctxRecord.getBctxType() + " not found");
				}
			} catch(Exception ex) {
				sendLog = new BctxLog();
				sendLog.setSuccessFlag(false);
				sendLog.setErrorcode(ex.getClass().getSimpleName());
				sendLog.setErrormessage(ex.getMessage());
			}

			try {
				BctxLogRecord logRecord = new BctxLogRecord();
				logRecord.setBctxId(bctxRecord.getId());
				logRecord.setRequest(sendLog.getRequest());
				logRecord.setResponse(sendLog.getResponse());
				logRecord.setBcRefId(sendLog.getBcRefId());
				logRecord.setSuccessFlag(sendLog.getSuccessFlag());
				logRecord.setErrorcode(sendLog.getErrorcode());
				logRecord.setErrormessage(sendLog.getErrormessage());

				if(sendLog.getSuccessFlag() != null && sendLog.getSuccessFlag()) {
					logger.info("[BCTX#{}] BcTx Success", bctxRecord.getId());
					bctxRecord.setStatus(BctxStatusEnum.FINISHED);
					bctxRecord.setBcRefId(sendLog.getBcRefId());
				} else {
					logger.info("[BCTX#{}] BcTx Failed, {} : {}", bctxRecord.getId(), logRecord.getErrorcode(), logRecord.getErrormessage());
					bctxRecord.setStatus(BctxStatusEnum.FAILED);
					// FIXME : AUTORETRY DISABLED FOR SAFETY
//					bctxRecord.setScheduleTimestamp(UTCUtil.getNow().plusSeconds(RETRY_INTERVAL));
				}

				dslContext.attach(logRecord);

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
