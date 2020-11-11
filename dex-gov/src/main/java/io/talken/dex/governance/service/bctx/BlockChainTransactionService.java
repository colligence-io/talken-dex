package io.talken.dex.governance.service.bctx;

import com.google.common.base.Throwables;
import io.talken.common.exception.TalkenException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.jooq.tables.pojos.Bctx;
import io.talken.common.persistence.jooq.tables.records.BctxLogRecord;
import io.talken.common.persistence.jooq.tables.records.BctxRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.SingleKeyTable;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.service.bctx.txsender.EthereumErc20TxSender;
import io.talken.dex.governance.service.bctx.txsender.EthereumTxSender;
import io.talken.dex.shared.TokenMetaTable;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static io.talken.common.persistence.jooq.Tables.BCTX;
import static io.talken.common.persistence.jooq.Tables.BCTX_LOG;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class BlockChainTransactionService implements ApplicationContextAware {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(BlockChainTransactionService.class);

    // constructor autowire with lombok
	private final DSLContext dslContext;

	// constructor autowire with lombok
	private final DataSourceTransactionManager txMgr;

    // constructor autowire with lombok
    private final AdminAlarmService alarmService;

	private final SingleKeyTable<BlockChainPlatformEnum, TxSender> txSenders = new SingleKeyTable<>();
	private final Map<BlockChainPlatformEnum, TxMonitor> txMonitors = new HashMap<>();

	private ApplicationContext applicationContext;

	private final static int RETRY_INTERVAL = 300;
    private final static int PENDING_CHECK_MIN = 3; // TODO: fix 30 min

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@PostConstruct
	private void init() {
		Map<String, TxSender> txsBeans = applicationContext.getBeansOfType(TxSender.class);
		txsBeans.forEach((_name, _bean) -> {
			txSenders.insert(_bean);
			logger.info("TxSender {} for [{}] registered.", _bean.getClass().getSimpleName(), _bean.getPlatform());
		});

		Map<String, TxMonitor> txmBeans = applicationContext.getBeansOfType(TxMonitor.class);
		txmBeans.forEach((_name, _bean) -> {
			for(BlockChainPlatformEnum bcType : _bean.getBcTypes()) {
				if(txMonitors.containsKey(bcType)) throw new IllegalStateException(bcType.toString() + " already registered.");
				txMonitors.put(bcType, _bean);
				logger.info("TxMonitor {} for [{}] registered.", _bean.getClass().getSimpleName(), bcType);
			}
		});
	}

	@Scheduled(fixedDelay = 30 * 1000, initialDelay = 5000) // check sent tx every 30 seconds
	private synchronized void checkSent() {
		if(DexGovStatus.isStopped) return;

		// check 30sec old ~ 7 days old sent txs
		Result<BctxRecord> txQueue = dslContext.selectFrom(BCTX)
				.where(BCTX.STATUS.eq(BctxStatusEnum.SENT)
						.and(BCTX.UPDATE_TIMESTAMP.isNotNull()
                                .and(BCTX.UPDATE_TIMESTAMP.le(UTCUtil.getNow().minusSeconds(30)))
                                .and(BCTX.UPDATE_TIMESTAMP.gt(UTCUtil.getNow().minusDays(7))))
				).fetch();

		if(txQueue.isNotEmpty()) {
			logger.info("Checking {} sent(pending) txs...", txQueue.size());
			for(BctxRecord bctxRecord : txQueue) {
			    if (bctxRecord.getStatus().equals(BctxStatusEnum.SENT) && bctxRecord.getBcRefId() == null) {
                    logger.warn("Wait for check sent(pending) tx [BCTX#{}]", bctxRecord.getId());
                    continue;
                }
				try {

					if(txMonitors.containsKey(bctxRecord.getBctxType())) {
					    txMonitors.get(bctxRecord.getBctxType()).checkTransactionStatus(bctxRecord.getBcRefId());
                        // status 업데이트 확인
                        //recoveryPending(bctxRecord);
                    }
				} catch(Exception ex) {
					logger.exception(ex, "Cannot check pending transaction [BCTX#{}] / {}", bctxRecord.getId(), bctxRecord.getBcRefId());
				}
			}
		}
	}

	@Scheduled(fixedDelay = 1000, initialDelay = 3000)
	private synchronized void checkQueue() {
		if(DexGovStatus.isStopped) return;

		Result<BctxRecord> txQueue = dslContext.selectFrom(BCTX)
				.where(BCTX.STATUS.eq(BctxStatusEnum.QUEUED)
						.and(BCTX.SCHEDULE_TIMESTAMP.isNull().or(BCTX.SCHEDULE_TIMESTAMP.le(UTCUtil.getNow())))
				).fetch();

		for(BctxRecord bctxRecord : txQueue) {
			if(DexGovStatus.isStopped) return;

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
			} catch(TalkenException ex) {
				logRecord.setStatus(BctxStatusEnum.FAILED);
				logRecord.setErrorcode(ex.getClass().getSimpleName());
				logRecord.setErrormessage(ex.getMessage());
			} catch(Exception ex) {
				logger.exception(ex);
				logRecord.setStatus(BctxStatusEnum.FAILED);
				logRecord.setErrorcode("Exception");
				StringBuilder sb = new StringBuilder(ex.getClass().getSimpleName()).append(" : ").append(ex.getMessage()).append("\nStacktrace :\n");
				try {
					sb.append(Throwables.getStackTraceAsString(ex));
				} catch(Exception ex2) {
					logger.exception(ex2);
				}
				logRecord.setErrormessage(sb.toString());
			}

			try {
				// force lowercase of ref_id (mostly txHash) for search
				if(logRecord.getBcRefId() != null) {
					logRecord.setBcRefId(logRecord.getBcRefId().toLowerCase());
				}

				if(logRecord.getStatus().equals(BctxStatusEnum.SENT)) {
					logger.info("[BCTX#{}] BcTx Successfully sent, bcRef = {}", bctxRecord.getId(), logRecord.getBcRefId());
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

	private void recoveryPending(BctxRecord bctxRecord) throws TokenMetaNotFoundException {
        // cond check #1 status 업데이트 없으면, 시간 30분 넘으면
        // cond check #2 ETH anchor, deanchor
        if (bctxRecord.getStatus().equals(BctxStatusEnum.SENT) && bctxRecord.getBcRefId() != null) {
            LocalDateTime now = UTCUtil.getNow();
            LocalDateTime bctxCreate = bctxRecord.getCreateTimestamp();

            Duration duration = Duration.between(now, bctxCreate);
            long diff = Math.abs(duration.toMinutes());

            if (diff > PENDING_CHECK_MIN) {
                // TODO: syslogin0809 : 0x3Eef31524C233fF8cB783e9E000DBDB39cD8e6b3
                alarmService.warn(logger,"[TEST] BCTX MON(diff {} m) : [BCTX#{}] / {}, sym {}, amt {}, stat {}",
                        diff, bctxRecord.getId(), bctxRecord.getBcRefId(), bctxRecord.getSymbol(), bctxRecord.getAmount().stripTrailingZeros(), bctxRecord.getStatus());
                Bctx bctx = bctxRecord.into(BCTX).into(Bctx.class);
                BctxLogRecord lastLog = dslContext.selectFrom(BCTX_LOG)
                        .where(BCTX_LOG.BCTX_ID.eq(bctx.getId()))
                        .fetchAny();

                if (bctxRecord.getBctxType().equals(BlockChainPlatformEnum.ETHEREUM)) {
                    if (txSenders.has(BlockChainPlatformEnum.ETHEREUM)) {
                        EthereumTxSender txSender = (EthereumTxSender) txSenders.select(BlockChainPlatformEnum.ETHEREUM);
                        TokenMetaTable.Meta meta = txSender.getTokenMeta(bctx.getSymbol());
                        alarmService.warn(logger,"[TEST] BCTX ETH SEND Recovery : [BCTX#{}] / meta {}, log", meta, lastLog);
                        // TODO : sendTx
                        // txSender.sendTxWithNonce(null, meta.getUnitDecimals(), bctx, lastLog);
                    } else {
                        logger.error("No txSender [BCTX#{}] / {}", bctxRecord.getId(), bctxRecord.getBcRefId());
//                                        lastLog.setStatus(BctxStatusEnum.FAILED);
//                                        lastLog.setErrorcode("NoTxSender");
//                                        lastLog.setErrormessage("TxSender " + bctxRecord.getBctxType() + " not found");
//                                        lastLog.store();
//                                        dslContext.attach(lastLog);
                    }

                } else if (bctxRecord.getBctxType().equals(BlockChainPlatformEnum.ETHEREUM_ERC20_TOKEN)) {
                    if (txSenders.has(BlockChainPlatformEnum.ETHEREUM_ERC20_TOKEN)) {
                        EthereumErc20TxSender txSender = (EthereumErc20TxSender) txSenders.select(BlockChainPlatformEnum.ETHEREUM_ERC20_TOKEN);

                        TokenMetaTable.Meta meta = txSender.getTokenMeta(bctx.getSymbol());
                        String metaCA = txSender.getMetaCA(meta, bctx);
                        String bctxCA = txSender.getBctxCA(bctx);

                        if((metaCA != null && bctxCA != null) && metaCA.equals(bctxCA)) {
                            // TODO : sendTx
                            // txSender.sendTxWithNonce(null, meta.getUnitDecimals(), bctx, lastLog);
                            alarmService.warn(logger,"[TEST] BCTX ERC20 SEND Recovery : [BCTX#{}] / meta {}, log", meta, lastLog);
                        } else {
                            alarmService.warn(logger,"[TEST] BCTX ERC20 SEND ERR : [BCTX#{}] / meta {}, log", meta, lastLog);
//                                            lastLog.setStatus(BctxStatusEnum.FAILED);
//                                            lastLog.setErrorcode("CONTRACT_ID_NOT_MATCH");
//                                            lastLog.setErrormessage("CONTRACT_ID of bctx is not match with TokenMeta.");
//                                            lastLog.store();
//                                            dslContext.attach(lastLog);
                        }
                    } else {
                        logger.error("No txSender [BCTX#{}] / {}", bctxRecord.getId(), bctxRecord.getBcRefId());
                        //                                        lastLog.setStatus(BctxStatusEnum.FAILED);
//                                        lastLog.setErrorcode("NoTxSender");
//                                        lastLog.setErrormessage("TxSender " + bctxRecord.getBctxType() + " not found");
//                                        lastLog.store();
//                                        dslContext.attach(lastLog);
                    }
                }
            }
        }
    }
}
