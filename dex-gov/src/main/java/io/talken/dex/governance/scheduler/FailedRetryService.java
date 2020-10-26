package io.talken.dex.governance.scheduler;

import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.jooq.tables.records.BctxRecord;
import io.talken.common.persistence.jooq.tables.records.UserRewardRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import io.talken.dex.shared.service.integration.signer.SignServerService;
import io.talken.dex.shared.service.tradewallet.TradeWalletService;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static io.talken.common.CommonConsts.ZONE_UTC;
import static io.talken.common.persistence.jooq.Tables.*;

@Service
@Scope("singleton")
public class FailedRetryService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(FailedRetryService.class);

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
    private SignServerService signServerService;

    @Autowired
    private StellarNetworkService stellarNetworkService;

    private int tickLimit = 15;

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
    @Scheduled(cron = "0 0/30 * * * *", zone = ZONE_UTC)
    private synchronized void FailedBctxRetry() {
        if(isSuspended) return;
        if(DexGovStatus.isStopped) return;

        long ts = UTCUtil.getNowTimestamp_s();
        logger.debug("FailedBctxRetry Scheduled...{}", ts);

        Cursor<BctxRecord> bctxRecords = dslContext.selectFrom(BCTX)
                .where(BCTX.STATUS.eq(BctxStatusEnum.FAILED))
                .limit(tickLimit)
                .fetchLazy();

        try {
            checkMissed(bctxRecords, ts);
        } catch(Exception ex) {
            alarmService.exception(logger, ex);
        }
    }

    @Scheduled(cron = "0 15/45 * * * *", zone = ZONE_UTC)
    private void FailedUserRewardRetry() {
        if(isSuspended) return;
        if(DexGovStatus.isStopped) return;

        long ts = UTCUtil.getNowTimestamp_s();
        logger.debug("FailedUserRewardRetry Scheduled...{}", ts);

//        Cursor<UserRewardRecord> userRewardRecords = dslContext.selectFrom(USER_REWARD)
//                .where(USER_REWARD.ERRORCODE.isNotNull())
//                .limit(tickLimit)
//                .fetchLazy();
//
//        try {
//            checkMissed(userRewardRecords, ts);
//        } catch(Exception ex) {
//            alarmService.exception(logger, ex);
//        }
    }

    // TODO : merge dup and optimize code with API
    private <T extends Record> void checkMissed(Cursor<T> records, long timestamp) {
        while(records.hasNext()) {
            Record record = records.fetchNext();
            // TODO : record Type Check
            if (record instanceof BctxRecord) {
                BctxRecord bctxRecord = (BctxRecord) record;
                logger.debug("BctxRecord : {}", bctxRecord);

                bctxRecord.setStatus(BctxStatusEnum.QUEUED);
                dslContext.attach(bctxRecord);
                bctxRecord.store();

            } else if (record instanceof UserRewardRecord) {
                UserRewardRecord userRewardRecord = (UserRewardRecord) record;
                logger.debug("UserRewardRecord : {}", userRewardRecord);

                // TODO : if trade_wallet exists.
                Boolean activationConfirmed = dslContext.select(USER_TRADE_WALLET.ACTIVATIONCONFIRMED)
                        .from(USER_TRADE_WALLET)
                        .where(USER_TRADE_WALLET.USER_ID.eq(userRewardRecord.getUserId()))
                        .fetchAnyInto(Boolean.class);

                // TODO : is it real activate on stellar network

                if (activationConfirmed) {
                    userRewardRecord.setCheckFlag(Boolean.FALSE);
                    userRewardRecord.setErrorcode(null);
                    userRewardRecord.setErrormessage(null);
                    dslContext.attach(userRewardRecord);
                    userRewardRecord.store();
                } else {
                    // TODO : remove tradeWallet
                }

            } else {
                logger.debug("NONE RETRY");
            }
        }
    }

    private User getUser(long userId) {
        return dslContext.selectFrom(USER).where(USER.ID.eq(userId)).fetchAnyInto(User.class);
    }
}
