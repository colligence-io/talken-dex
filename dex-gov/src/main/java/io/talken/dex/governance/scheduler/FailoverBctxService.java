package io.talken.dex.governance.scheduler;

import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.jooq.tables.pojos.User;
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

import static io.talken.common.CommonConsts.ZONE_UTC;
import static io.talken.common.persistence.jooq.Tables.*;
import static org.jooq.impl.DSL.any;

@Service
@Scope("singleton")
public class FailoverBctxService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(FailoverBctxService.class);

    @Autowired
    private DSLContext dslContext;

    @Autowired
    protected DataSourceTransactionManager txMgr;

    @Autowired
    private AdminAlarmService alarmService;

    @Autowired
    private TradeWalletService twService;


    private int tickLimit = 100;

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
     * retry failed record, queue
     */
//    @Scheduled(cron = "0 0/30 * * * *", zone = ZONE_UTC)
    @Scheduled(fixedDelay = 60 * 1000 * 5, initialDelay = 10000)
    private synchronized void FailedBctxRetry() {
        if(isSuspended) return;
        if(DexGovStatus.isStopped) return;

        long ts = UTCUtil.getNowTimestamp_s();
        logger.debug("FailedBctxRetry Scheduled...{}", ts);

        Cursor<BctxRecord> bctxRecords = dslContext.selectFrom(BCTX)
                .where(BCTX.STATUS.eq(BctxStatusEnum.FAILED)
                .and(BCTX.TX_AUX.like(any("TALKENH%","TALKENI%","TALKENJ%","TALKENL%"))))
                .limit(tickLimit)
                .fetchLazy();

        try {
            checkMissed(bctxRecords, ts);
        } catch(Exception ex) {
            alarmService.exception(logger, ex);
        }
    }

//    @Scheduled(cron = "0 15/45 * * * *", zone = ZONE_UTC)
    @Scheduled(fixedDelay = 60 * 1000 * 5, initialDelay = 5000)
    private void FailedUserRewardRetry() {
        if(isSuspended) return;
        if(DexGovStatus.isStopped) return;

        long ts = UTCUtil.getNowTimestamp_s();
        logger.debug("FailedUserRewardRetry Scheduled...{}", ts);

        Cursor<UserRewardRecord> userRewardRecords = dslContext.selectFrom(USER_REWARD)
                .where(USER_REWARD.ERRORCODE.isNotNull())
                .limit(tickLimit)
                .fetchLazy();

        try {
            checkMissed(userRewardRecords, ts);
        } catch(Exception ex) {
            alarmService.exception(logger, ex);
        }
    }

    // TODO : merge dup and optimize code with API
    private <T extends Record> void checkMissed(Cursor<T> records, long timestamp) throws TradeWalletCreateFailedException {
        while(records.hasNext()) {
            Record record = records.fetchNext();
            // TODO : record Type Check
            if (record instanceof BctxRecord) {
                BctxRecord bctxRecord = (BctxRecord) record;
                logger.debug("BctxRecord : {} : {}", bctxRecord.getId(), bctxRecord.toString());

//                bctxRecord.setStatus(BctxStatusEnum.QUEUED);
//                dslContext.attach(bctxRecord);
//                bctxRecord.store();

            } else if (record instanceof UserRewardRecord) {
                UserRewardRecord userRewardRecord = (UserRewardRecord) record;
                logger.debug("UserRewardRecord : {} : {}", userRewardRecord.getId(), userRewardRecord.toString());

                long userId = userRewardRecord.getUserId();
//
                UserTradeWalletRecord utwRecord = dslContext.selectFrom(USER_TRADE_WALLET)
                        .where(USER_TRADE_WALLET.USER_ID.eq(userId))
                        .fetchAny();
                if (utwRecord != null) {
                    AccountResponse ar = twService.getAccountInfoFromStellar(utwRecord.getAccountid());
                    if(ar != null) {
                        logger.debug("UserTradeWalletRecord : {} : {}, {}", userId, utwRecord.toString(), ar);
//                        resetUserReward(userRewardRecord);
                    } else {
                        logger.debug("UserTradeWalletRecord NOT CONFIRMED : {} : {}", userId, utwRecord.toString());
//                        utwRecord.delete();
//                        dslContext.attach(utwRecord);
//                        resetUserReward(userRewardRecord);
                    }
                } else {
                    logger.debug("UserTradeWallet NULL : {}", userId);
                    resetUserReward(userRewardRecord);
                }
            } else {
                logger.debug("NONE RETRY");
            }
        }
    }

    private void resetUserReward(UserRewardRecord userRewardRecord) {
        userRewardRecord.setCheckFlag(Boolean.FALSE);
        userRewardRecord.setErrorcode(null);
        userRewardRecord.setErrormessage(null);
        dslContext.attach(userRewardRecord);
        userRewardRecord.store();
    }

    private User getUser(long userId) {
        return dslContext.selectFrom(USER).where(USER.ID.eq(userId)).fetchAnyInto(User.class);
    }
}
