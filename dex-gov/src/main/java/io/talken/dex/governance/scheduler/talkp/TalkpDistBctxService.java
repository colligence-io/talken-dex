package io.talken.dex.governance.scheduler.talkp;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.enums.UserPointDistStatusEnum;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.jooq.tables.records.BctxRecord;
import io.talken.common.persistence.jooq.tables.records.TalkpDistHistoryRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.ObjectPair;
import io.talken.common.util.collection.SingleKeyTable;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.scheduler.talkreward.DistStatus;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.TransactionBlockExecutor;
import io.talken.dex.shared.exception.TradeWalletCreateFailedException;
import io.talken.dex.shared.exception.TradeWalletRebalanceException;
import io.talken.dex.shared.service.blockchain.stellar.StellarChannelTransaction;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import io.talken.dex.shared.service.tradewallet.TradeWalletInfo;
import io.talken.dex.shared.service.tradewallet.TradeWalletService;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import java.math.BigDecimal;

import static io.talken.common.persistence.jooq.Tables.*;

@Service
@Scope("singleton")
public class TalkpDistBctxService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TalkpDistBctxService.class);

	@Autowired
	private DSLContext dslContext;

	@Autowired
	protected DataSourceTransactionManager txMgr;

	@Autowired
	private AdminAlarmService alarmService;

	@Autowired
	private TokenMetaGovService tmService;

	@Autowired
	private TradeWalletService twService;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	private static final int tickLimit = 100;

    private static final String TALKP = "TALKP";

	private boolean isSuspended = false;

    private final String _CLAZZ_NAME = this.getClass().getSimpleName();


	public void suspend() {
		logger.info("{} SUSPENDED by admin.", _CLAZZ_NAME);
		isSuspended = true;
	}

	public void resume() {
		logger.info("{} RESUMED by admin.", _CLAZZ_NAME);
		isSuspended = false;
	}

	/**
	 * check talkp, queue bctx
	 */
	@Scheduled(fixedDelay = 60 * 1000 * 3, initialDelay = 4000)
	private void talkpToBctx() {
		if(isSuspended) return;
		if(DexGovStatus.isStopped) return;

		long ts = UTCUtil.getNowTimestamp_s();
		try {
			// do new rewards first
			checkTalkpAndQueueBctx(
					dslContext.selectFrom(TALKP_DIST_HISTORY)
							.where(TALKP_DIST_HISTORY.APPROVEMENT_FLAG.eq(true)
									.and(TALKP_DIST_HISTORY.CHECK_FLAG.eq(false))
									.and(TALKP_DIST_HISTORY.BCTX_ID.isNull())
							)
							.limit(tickLimit)
							.fetchLazy(), ts
			);
            checkTalkpAndQueueBctxComplete(
                    dslContext.selectFrom(TALKP_DIST_HISTORY)
                            .where(TALKP_DIST_HISTORY.APPROVEMENT_FLAG.eq(true)
                                    .and(TALKP_DIST_HISTORY.CHECK_FLAG.eq(true))
                                    .and(TALKP_DIST_HISTORY.BCTX_ID.isNotNull())
                            )
                            .limit(tickLimit)
                            .fetchLazy(), ts
            );
		} catch(Exception ex) {
			alarmService.exception(logger, ex);
		}
	}

	/**
	 * process reward record, queue bctx, update reward record
	 *
	 * @param talkpDistHistoryRecords
	 * @param timestamp
	 */
	private void checkTalkpAndQueueBctx(Cursor<TalkpDistHistoryRecord> talkpDistHistoryRecords, long timestamp) {
		SingleKeyTable<String, DistStatus> dStatus = new SingleKeyTable<>();

		while(talkpDistHistoryRecords.hasNext()) {
            TalkpDistHistoryRecord talkpDistHistoryRecord = talkpDistHistoryRecords.fetchNext();

			TokenMetaTable.Meta meta;
			try {
				meta = tmService.getTokenMeta(TALKP);
			} catch(TokenMetaNotFoundException ex) {
                alarmService.info(logger, "TALKP distribute Error : no meta found. {} process is skipped.", talkpDistHistoryRecord.getId());
				continue;
			}

            BctxRecord bctxRecord = null;

			try {
                bctxRecord = processTradeWalletTalkpDist(talkpDistHistoryRecord, meta);

				if(bctxRecord != null) {
                    final BctxRecord _BctxRecord = bctxRecord;
                    TransactionBlockExecutor.of(txMgr).transactional(() -> {
						dslContext.attach(_BctxRecord);
                        _BctxRecord.store();

                        talkpDistHistoryRecord.setBctxId(_BctxRecord.getId());
                        talkpDistHistoryRecord.setCheckFlag(true);
                        talkpDistHistoryRecord.store();

                        updateUserPointDistStatus(talkpDistHistoryRecord, UserPointDistStatusEnum.TX_SENT);
					});

					if(!dStatus.has(TALKP)) dStatus.insert(new DistStatus(meta.getSymbol()));
					DistStatus distStatus = dStatus.select(TALKP);
                    distStatus.getCount().incrementAndGet();
                    distStatus.setAmount(distStatus.getAmount().add(talkpDistHistoryRecord.getAmount()));
				}
			} catch(Exception ex) {
				StringBuilder sb = new StringBuilder();
				if (bctxRecord != null) {
                    sb.append("[BCTX:")
                            .append(bctxRecord.getId())
                            .append("] ")
                            .append(ex.getMessage());
                    talkpDistHistoryRecord.setBctxId(bctxRecord.getId()   );
                } else {
                    sb.append(ex.getMessage());
                }

				String errorMessage = sb.toString();
                alarmService.error(logger, "TALKP distribute failed : {} {}", ex.getClass().getSimpleName(), errorMessage);
                alarmService.exception(logger, ex);

                talkpDistHistoryRecord.setErrorcode(ex.getClass().getSimpleName());
                talkpDistHistoryRecord.setErrormessage(errorMessage);
                talkpDistHistoryRecord.setCheckFlag(true);
                talkpDistHistoryRecord.store();

                updateUserPointDistStatus(talkpDistHistoryRecord, UserPointDistStatusEnum.TX_ERROR);
			}
		}

		for(DistStatus distStatus : dStatus.select()) {
			if(distStatus.getCount().get() > 0) {
				alarmService.info(logger, "TALKP distribute Queued [{}]: {} {} ({} transaction)",
                    timestamp, distStatus.getAmount().stripTrailingZeros().toPlainString(), distStatus.getAssetCode(), distStatus.getCount().get());
			}
		}
	}

    private void checkTalkpAndQueueBctxComplete(Cursor<TalkpDistHistoryRecord> talkpDistHistoryRecords, long timestamp) {
        while(talkpDistHistoryRecords.hasNext()) {
            TalkpDistHistoryRecord talkpDistHistoryRecord = talkpDistHistoryRecords.fetchNext();

            BctxRecord bctxRecord = null;

            try {
                bctxRecord = dslContext.selectFrom(BCTX).where(BCTX.ID.eq(talkpDistHistoryRecord.getBctxId())).fetchAny();

                if(bctxRecord != null) {
                    final BctxRecord _BctxRecord = bctxRecord;
                    TransactionBlockExecutor.of(txMgr).transactional(() -> {
                        if (_BctxRecord.getStatus().equals(BctxStatusEnum.SUCCESS)) {
                            updateUserPointDistStatus(talkpDistHistoryRecord, UserPointDistStatusEnum.TX_OK);
                        } else if (_BctxRecord.getStatus().equals(BctxStatusEnum.FAILED)) {
                            updateUserPointDistStatus(talkpDistHistoryRecord, UserPointDistStatusEnum.TX_ERROR);
                        }
                    });
                }
            } catch(Exception ex) {
                // TODO : fix merge dup code
                StringBuilder sb = new StringBuilder();
                if (bctxRecord != null) {
                    sb.append("[BCTX:")
                            .append(bctxRecord.getId())
                            .append("] ")
                            .append(ex.getMessage());
                    talkpDistHistoryRecord.setBctxId(bctxRecord.getId()   );
                } else {
                    sb.append(ex.getMessage());
                }

                String errorMessage = sb.toString();
                alarmService.error(logger, "UserPointHistory BCTX status update failed : {} {}", ex.getClass().getSimpleName(), errorMessage);
                alarmService.exception(logger, ex);

                talkpDistHistoryRecord.setErrorcode(ex.getClass().getSimpleName());
                talkpDistHistoryRecord.setErrormessage(errorMessage);
                talkpDistHistoryRecord.setCheckFlag(true);
                talkpDistHistoryRecord.store();

                updateUserPointDistStatus(talkpDistHistoryRecord, UserPointDistStatusEnum.TX_ERROR);
            }
        }
    }

	/**
	 * user_reward to bctx record for trade wallet
	 *
	 * @param talkpDistHistoryRecord
	 * @param meta
	 * @return
	 * @throws TradeWalletCreateFailedException
	 * @throws TradeWalletRebalanceException
	 */

	// TODO : fix exception or make retried exception rows fetch scheduler
    // TODO : fix merge dup code
	private BctxRecord processTradeWalletTalkpDist(TalkpDistHistoryRecord talkpDistHistoryRecord, TokenMetaTable.Meta meta) throws TradeWalletCreateFailedException, TradeWalletRebalanceException {
		if(!meta.isManaged()) {
			alarmService.error(logger, "TALKP Error : {} is not managed asset", meta.getSymbol());
			return null;
		}

		User user = dslContext.selectFrom(USER).where(USER.ID.eq(talkpDistHistoryRecord.getUserId())).fetchOneInto(User.class);
		TradeWalletInfo tradeWallet = twService.ensureTradeWallet(user);

		StellarChannelTransaction.Builder sctxBuilder = stellarNetworkService.newChannelTxBuilder();
		try {
			ObjectPair<Boolean, BigDecimal> rebalanced = twService.addNativeBalancingOperation(sctxBuilder, tradeWallet, false, meta.getManagedInfo().getAssetCode());
			if(rebalanced.first()) {
				logger.debug("Rebalance trade wallet {} (#{}) for TALKP dist.", tradeWallet.getAccountId(), user.getId());
				SubmitTransactionResponse rebalanceResponse = sctxBuilder.buildAndSubmit();
				if(!rebalanceResponse.isSuccess()) {
					ObjectPair<String, String> errorInfo = StellarConverter.getResultCodesFromExtra(rebalanceResponse);
					logger.error("Cannot rebalance trade wallet {} {} : {} {}", user.getId(), tradeWallet.getAccountId(), errorInfo.first(), errorInfo.second());
					throw new TradeWalletRebalanceException(errorInfo.first());
				}
			}
		} catch(TradeWalletRebalanceException ex) {
			throw ex;
		} catch(Exception ex) {
			throw new TradeWalletRebalanceException(ex, "Cannot rebalance trade wallet. : " + ex.getClass().getName());
		}

		BctxRecord bctxRecord = new BctxRecord();
		bctxRecord.setStatus(BctxStatusEnum.QUEUED);
		bctxRecord.setBctxType(BlockChainPlatformEnum.STELLAR_TOKEN);
		bctxRecord.setSymbol(meta.getManagedInfo().getAssetCode());
		bctxRecord.setPlatformAux(meta.getManagedInfo().getIssuerAddress());
		bctxRecord.setAddressFrom(meta.getManagedInfo().getIssuerAddress());
		bctxRecord.setAddressTo(tradeWallet.getAccountId());
		bctxRecord.setAmount(talkpDistHistoryRecord.getAmount());
		bctxRecord.setNetfee(BigDecimal.ZERO);

		DexTaskId taskId = DexTaskId.generate_taskId(DexTaskTypeEnum.ISSUE_TALKP);

		bctxRecord.setTxAux(taskId.getId());

		return bctxRecord;
	}

	private synchronized void updateUserPointDistStatus(TalkpDistHistoryRecord record, UserPointDistStatusEnum status) {
        long userId = record.getUserId();
        dslContext.update(USER_POINT_HISTORY)
                .set(USER_POINT_HISTORY.DIST_STATUS, status)
                .where(USER_POINT_HISTORY.USER_ID.eq(userId)
                        .and(USER_POINT_HISTORY.TTS.ge(record.getTtsFrom())
                                .and(USER_POINT_HISTORY.TTS.le(record.getTtsTo()))
                        )
                )
                .execute();
    }
}