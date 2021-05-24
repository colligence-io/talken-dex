package io.talken.dex.governance.service.bctx;

import com.google.common.base.Throwables;
import io.talken.common.exception.TalkenException;
import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
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
import io.talken.dex.shared.service.blockchain.ethereum.EthereumNetworkService;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static io.talken.common.persistence.jooq.Tables.BCTX;

/**
 * The type Block chain transaction service.
 */
@Service
@Scope("singleton")
@RequiredArgsConstructor
public class BlockChainTransactionService implements ApplicationContextAware {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(BlockChainTransactionService.class);

    @Autowired
    private EthereumNetworkService ethNetworkService;

    @Autowired
	private DSLContext dslContext;

    @Autowired
	private DataSourceTransactionManager txMgr;

    @Autowired
    private AdminAlarmService alarmService;

	private final SingleKeyTable<BlockChainPlatformEnum, TxSender> txSenders = new SingleKeyTable<>();
	private final Map<BlockChainPlatformEnum, TxMonitor> txMonitors = new HashMap<>();

	private ApplicationContext applicationContext;

	private final static int RETRY_INTERVAL = 300;

	// retry ETH, ERC20
    private final static int RETRY_DELAYED = 30; // TODO: fix 30 min

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
                        checkPending(bctxRecord);
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
                } else if (BlockChainPlatformEnum.KLAYTN.equals(bctxRecord.getBctxType())
                        || BlockChainPlatformEnum.KLAYTN_KIP7_TOKEN.equals(bctxRecord.getBctxType())
                        || BlockChainPlatformEnum.KLAYTN_KIP17_TOKEN.equals(bctxRecord.getBctxType())
                ){
                    // TODO: for Klaytn bctx
				    alarmService.warn(logger, "Do use Sender for {}", bctxRecord.getBctxType());

                    logRecord.setStatus(BctxStatusEnum.FAILED);
                    logRecord.setErrorcode("NoTxSender");
                    logRecord.setErrormessage("TxSender " + bctxRecord.getBctxType() + " not found");
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

	private void checkPending(BctxRecord bctxRecord) throws Exception {
        // TODO : cond check #0 BctxStatusEnum.SENT and txHash is not null
        // TODO : cond check #1 transaction type ETH, ERC20 (only deanchor, without anchor)
        // TODO : cond check #2 status(SENT -> SUCCESS) not update and txSend delayed 30 min
	    // TODO : cond check #3 txHash status

        // TODO : with every bctxType transaction(BITCOIN,LUNIVERSE,FILECOIN,etcs...)

        logger.info("[TEST] BCTX Check Pending : [BCTX#{}] / txHash {}",bctxRecord.getId(), bctxRecord.getBcRefId());

        // TODO: add check cond only for test
        // syslogin0809 : 0x3Eef31524C233fF8cB783e9E000DBDB39cD8e6b3

        final BlockChainPlatformEnum ETH = BlockChainPlatformEnum.ETHEREUM;
        final BlockChainPlatformEnum ERC20 = BlockChainPlatformEnum.ETHEREUM_ERC20_TOKEN;

        // TODO : cond check #1
        // TODO : only bctxType ETH, ERC20
        BlockChainPlatformEnum bctxType = bctxRecord.getBctxType();
        if (!(bctxType.equals(ETH) || bctxType.equals(ERC20))) return;

        // TODO : cond check #2
        // TODO : check status
        BctxStatusEnum bctxStatus = bctxRecord.getStatus();
        if (!(bctxStatus.equals(BctxStatusEnum.SENT) && bctxRecord.getBcRefId() != null)) return;

        // TODO : check Delayed
        LocalDateTime now = UTCUtil.getNow();
        LocalDateTime bctxCreate = bctxRecord.getCreateTimestamp();
        Duration duration = Duration.between(now, bctxCreate);
        long diff = Math.abs(duration.toMinutes());
        boolean isDelayed = diff > RETRY_DELAYED;
        if (!isDelayed) return;

        // TODO : cond check #3
        // TODO : check isTranscationPending
        // TODO : use choice tx or txReceipt
        String txHash = bctxRecord.getBcRefId();

        Transaction tx = ethNetworkService.getEthTransaction(txHash);
        TransactionReceipt txReceipt = ethNetworkService.getEthTransactionReceipt(txHash);

        // TODO : check transaction cond
        BigInteger nonce;
        boolean isPending = true;

        if (tx != null) {
            nonce = tx.getNonce();
            // TODO : check txBlockHash, txBlockNumber, txReceipt
            if (tx.getBlockHash() != null && tx.getBlockNumberRaw() != null && txReceipt != null) {
                logger.info("[TEST] BCTX tx : [BCTX#{}] / txBlockHash {}, txBlockNumber {}, receipt {}",
                        bctxRecord.getId(), tx.getBlockHash(), tx.getBlockNumberRaw(), txReceipt);
                isPending = false;
            }
        } else {
            logger.error("[TEST] BCTX tx : [BCTX#{}] / Cannot find Tx(skipNotConfirmedTx)");
            return;
        }

        if (!isPending) return;
        logger.info("[TEST] BCTX Pending is TRUE : [BCTX#{}] / txHash {}",bctxRecord.getId(), bctxRecord.getBcRefId());

        // TODO : set New BctxLog
        BctxLogRecord log = new BctxLogRecord();
        Bctx bctx = bctxRecord.into(BCTX).into(Bctx.class);

        String errorMessage;

        log.setBctxId(bctx.getId());
        log.setStatus(bctx.getStatus());

        TxSender txSender = txSenders.select(bctxType);
        TokenMetaTable.Meta meta = txSender.getTokenMeta(bctx.getSymbol());

        if (bctxType.equals(ETH)) {
            if (txSenders.has(ETH)) {
                logger.info("[TEST] BCTX Retry Send Pending Tx : [BCTX#{}] / txHash {}",bctxRecord.getId(), bctxRecord.getBcRefId());
                // TODO : sendTx
                EthereumTxSender ethTxSender = (EthereumTxSender) txSenders.select(ETH);
//                ethTxSender.sendTxWithNonce(null, meta.getUnitDecimals(), bctx, log, nonce);
            } else {
                errorMessage = bctxType + " TxSender not found";
                txSender.setBctxLogFailedNoTxSender(logger, log, bctx, TxSender.ErrorCode.NO_TX_SENDER, errorMessage);
                log.store();
            }
        } else {
            if (txSenders.has(ERC20)) {
                EthereumErc20TxSender erc20TxSender = (EthereumErc20TxSender) txSenders.select(ERC20);
                String metaCA = erc20TxSender.getMetaCA(meta, bctx);
                String bctxCA = erc20TxSender.getBctxCA(bctx);

                if ((metaCA != null && bctxCA != null) && metaCA.equals(bctxCA)) {
                    // TODO : sendTx
                    String contractAddr = meta.getAux().get(TokenMetaAuxCodeEnum.ERC20_CONTRACT_ID).toString();
                    logger.info("[TEST] BCTX Retry Send Pending Tx : [BCTX#{}] / txHash {} / contractAddr {}",bctxRecord.getId(), bctxRecord.getBcRefId(), contractAddr);
//                    erc20TxSender.sendTxWithNonce(contractAddr, meta.getUnitDecimals(), bctx, log, nonce);
                } else {
                    errorMessage = "CONTRACT_ID of bctx is not match with TokenMeta";
                    txSender.setBctxLogFailedNoTxSender(logger, log, bctx, TxSender.ErrorCode.CONTRACT_ID_NOT_MATCH, errorMessage);
                    log.store();
                }
            } else {
                errorMessage = bctxType + " TxSender not found";
                txSender.setBctxLogFailedNoTxSender(logger, log, bctx, TxSender.ErrorCode.NO_TX_SENDER, errorMessage);
                log.store();
            }
        }
    }
}
