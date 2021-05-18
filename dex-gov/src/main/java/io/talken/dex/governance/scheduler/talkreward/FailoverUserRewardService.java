package io.talken.dex.governance.scheduler.talkreward;

//import io.talken.common.RunningProfile;

import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.jooq.tables.records.BctxRecord;
import io.talken.common.persistence.jooq.tables.records.UserRewardRecord;
import io.talken.common.persistence.jooq.tables.records.UserTradeWalletRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.shared.exception.TradeWalletCreateFailedException;
import io.talken.dex.shared.service.tradewallet.TradeWalletService;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.stellar.sdk.responses.AccountResponse;

import static io.talken.common.persistence.jooq.Tables.*;

//import static io.talken.common.CommonConsts.ZONE_UTC;

/**
 * The type Failover user reward service.
 */
@Service
@Scope("singleton")
public class FailoverUserRewardService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(FailoverUserRewardService.class);

    @Autowired
    private DSLContext dslContext;

    /**
     * The Tx mgr.
     */
    @Autowired
    protected DataSourceTransactionManager txMgr;

    @Autowired
    private AdminAlarmService alarmService;

    @Autowired
    private TradeWalletService twService;

    private int tickLimit = 100;

    private boolean isSuspended = true;

    private final String _CLAZZ_NAME = this.getClass().getSimpleName();

    /**
     * Suspend.
     */
    public void suspend() {
        logger.info("{} SUSPENDED by admin.", _CLAZZ_NAME);
        isSuspended = true;
    }

    /**
     * Resume.
     */
    public void resume() {
        logger.info("{} RESUMED by admin.", _CLAZZ_NAME);
        isSuspended = false;
    }
    /**
     * retry failed record, queue
     */

//    @Scheduled(cron = "0 15/45 * * * *", zone = ZONE_UTC)
    @Scheduled(fixedDelay = 60 * 1000 * 15, initialDelay = 5000)
    private synchronized void FailedUserRewardRetry() {
        // for TEST
//        if (RunningProfile.isProduction()) this.isSuspended = true;
        if(isSuspended) return;
        if(DexGovStatus.isStopped) return;

        long ts = UTCUtil.getNowTimestamp_s();
        logger.info("FailedUserRewardRetry Scheduled...{}", ts);

        Cursor<Record> records = dslContext
                .select()
                .from(USER_REWARD)
                .leftJoin(BCTX).on(BCTX.ID.eq(USER_REWARD.BCTX_ID))
                .where(USER_REWARD.PRIVATE_WALLET_FLAG.eq(Boolean.FALSE)
                        .and(BCTX.STATUS.eq(BctxStatusEnum.FAILED)
                                .or(USER_REWARD.ERRORCODE.isNotNull())
                        )
                )
                .limit(tickLimit)
                .fetchLazy();

        try {
            checkMissed(records, ts);
        } catch(Exception ex) {
            alarmService.exception(logger, ex);
        }
    }

    private <T extends Record> void checkMissed(Cursor<T> records, long timestamp) throws TradeWalletCreateFailedException {
        while(records.hasNext()) {
            Record record = records.fetchNext();

            UserRewardRecord userRewardRecord = record.into(UserRewardRecord.class);
            BctxRecord bctxRecord = record.into(BctxRecord.class);

            UserTradeWalletRecord utwRecord = dslContext
                    .selectFrom(USER_TRADE_WALLET)
                    .where(USER_TRADE_WALLET.USER_ID.eq(userRewardRecord.getUserId()))
                    .fetchAny();

            logger.debug("UserRewardRecord : {}", userRewardRecord);
            logger.debug("BctxRecord : {}", bctxRecord);
            logger.debug("UserTradeWalletRecord : {}", utwRecord);

            // TODO: first 리워드 오류
            if (userRewardRecord.getBctxId() != null && bctxRecord != null) {
                // BCTX failed retry
                if (bctxRecord.getStatus().equals(BctxStatusEnum.FAILED)) {
                    alarmService.info(logger, "BCTX failed retry [BCTX#{}] at UserReward_Id {}", bctxRecord.getId(), userRewardRecord.getId());
                    logger.info("BCTX failed retry [BCTX#{}] at UserReward_Id {}", bctxRecord.getId(), userRewardRecord.getId());
                    retryBctx(bctxRecord);
                }
            } else if (utwRecord != null) {
                AccountResponse ar = twService.getAccountInfoFromStellar(utwRecord.getAccountid());
                if(ar != null) {
                    // 지갑 계정 생성됨. account activation TRUE
                    // user_reward failed retry
                    alarmService.info(logger, "UserReward failed (0) retry UserReward_Id {}", userRewardRecord.getId());
                    logger.info("UserReward failed (0) retry UserReward_Id {}", userRewardRecord.getId());
                    retryUserReward(userRewardRecord);
                } else {
                    // 지갑 계정 생성됨. account activation FALSE
                    // 지갑 계정 삭제
                    // user_reward failed retry
                    alarmService.warn(logger, "UserReward failed (1) reset UserTradeWallet for User_Id {}", utwRecord.getUserId());
                    logger.warn("UserReward failed (1) reset UserTradeWallet for User_Id {}", utwRecord.getUserId());
                    utwRecord.delete();
                    dslContext.attach(utwRecord);
                    alarmService.warn(logger, "UserReward failed (2) retry UserReward_Id {}", userRewardRecord.getId());
                    logger.warn("UserReward failed (2) retry UserReward_Id {}", userRewardRecord.getId());
                    retryUserReward(userRewardRecord);
                }
            } else {
                // 지갑 없음
                // user_reward failed retry
                alarmService.info(logger, "UserReward failed (NO_WALLET) retry UserReward_Id {}", userRewardRecord.getId());
                logger.info("UserReward failed (NO_WALLET) retry UserReward_Id {}", userRewardRecord.getId());
                retryUserReward(userRewardRecord);
            }
        }
    }

    private void retryUserReward(UserRewardRecord userRewardRecord) {
        userRewardRecord.setCheckFlag(Boolean.FALSE);
        userRewardRecord.setErrorcode(null);
        userRewardRecord.setErrormessage(null);
        dslContext.attach(userRewardRecord);
        userRewardRecord.store();
    }

    private void retryBctx(BctxRecord bctxRecord) {
        bctxRecord.setStatus(BctxStatusEnum.QUEUED);
        dslContext.attach(bctxRecord);
        bctxRecord.store();
    }
}
