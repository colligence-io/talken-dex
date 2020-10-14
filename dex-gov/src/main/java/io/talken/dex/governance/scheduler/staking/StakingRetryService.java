package io.talken.dex.governance.scheduler.staking;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.talken.common.exception.TalkenException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.exception.common.TokenMetaNotManagedException;
import io.talken.common.persistence.DexTaskRecord;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.jooq.tables.records.DexTaskStakingRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.ObjectPair;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.TransactionBlockExecutor;
import io.talken.dex.shared.exception.SigningException;
import io.talken.dex.shared.exception.StellarException;
import io.talken.dex.shared.exception.TradeWalletRebalanceException;
import io.talken.dex.shared.service.blockchain.stellar.*;
import io.talken.dex.shared.service.integration.signer.SignServerService;
import io.talken.dex.shared.service.tradewallet.TradeWalletInfo;
import io.talken.dex.shared.service.tradewallet.TradeWalletService;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.stellar.sdk.AccountRequiresMemoException;
import org.stellar.sdk.Asset;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.PaymentOperation;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import java.io.IOException;
import java.math.BigDecimal;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_STAKING;
import static io.talken.common.persistence.jooq.Tables.USER;

@Service
@Scope("singleton")
public class StakingRetryService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(StakingRetryService.class);

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

    private static final int tickLimit = 100;

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
     * retry missed staking, queue
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 4000)
    private void stakingRetry() {
        if(isSuspended) return;
        if(DexGovStatus.isStopped) return;

        long ts = UTCUtil.getNowTimestamp_s();
        logger.debug("stakingRetry Scheduled...{}", ts);

        // unuse
        // Condition cond = DEX_TASK_STAKING.SIGNED_TX_CATCH_FLAG.isNull().or(DEX_TASK_STAKING.ERRORCODE.isNotNull());

        try {
            checkMissedStaking(
                    dslContext.selectFrom(DEX_TASK_STAKING)
                            .where(DEX_TASK_STAKING.SIGNED_TX_CATCH_FLAG.isFalse())
                            .limit(tickLimit)
                            .fetchLazy()
                    , ts
            );
        } catch(Exception ex) {
            alarmService.exception(logger, ex);
        }
    }

    // TODO : merge dup and optimize code with API
    private void checkMissedStaking(Cursor<DexTaskStakingRecord> missedStakings, long timestamp) {
        while(missedStakings.hasNext()) {
            DexTaskStakingRecord stakingRecord = missedStakings.fetchNext();

            // retry
            try {
                String position;
                User user = getUser(stakingRecord.getUserId());
                DexTaskTypeEnum taskType = stakingRecord.getTasktype();
                String stakingEventAssetCode = stakingRecord.getAssetcode();

                final TradeWalletInfo tradeWallet = twService.ensureTradeWallet(user);
                final DexTaskId regenDexTaskId = DexTaskId.generate_taskId(taskType);

                stakingRecord.setTaskid(regenDexTaskId.getId());

                position = "rebalance";
                if (stakingRecord.getErrorcode() != null && stakingRecord.getErrorcode().equals("10220")) {
                    rebalanceProcess(position, user, tradeWallet, stakingRecord);
                }

                final KeyPair issuerAccount = tmService.getManagedInfo(stakingEventAssetCode).dexIssuerAccount();
                final Asset stakingAssetType = tmService.getAssetType(stakingEventAssetCode);

                position = "build_tx";
                StellarChannelTransaction.Builder sctxBuilder = stellarNetworkService.newChannelTxBuilder()
                        .setMemo(stakingRecord.getTaskid());
                buildTxProcess(sctxBuilder, position, tradeWallet, issuerAccount, stakingAssetType, stakingRecord);

                position = "build_sctx";
                buildSctxProcess(sctxBuilder, position, stakingRecord);

                TransactionBlockExecutor.of(txMgr).transactional(() -> {
                    if (stakingRecord.getErrorcode() != null) {
                        stakingRecord.setErrorcode(null);
                        stakingRecord.setErrormessage(null);
                        stakingRecord.setErrorposition(null);
                    }

                    dslContext.attach(stakingRecord);
                    stakingRecord.store();
                });

            } catch(Exception ex) {
                alarmService.error(logger, "Staking retry failed : {} {}", ex.getClass().getSimpleName(), ex.getMessage());
                alarmService.exception(logger, ex);
                stakingRecord.setSignedTxCatchFlag(false);
                stakingRecord.store();
            }
        }
    }

    private void rebalanceProcess(String position, User user, TradeWalletInfo tradeWallet, DexTaskStakingRecord taskRecord)
            throws TradeWalletRebalanceException, SigningException, TokenMetaNotFoundException, StellarException, TokenMetaNotManagedException {
        // Adjust native balance before offer
        try {
            StellarChannelTransaction.Builder sctxBuilder = stellarNetworkService.newChannelTxBuilder();

            ObjectPair<Boolean, BigDecimal> rebalanced;
            rebalanced = twService.addNativeBalancingOperation(sctxBuilder, tradeWallet, true, taskRecord.getAssetcode());

            if(rebalanced.first()) {
                try {
                    logger.debug("Rebalance trade wallet {} (#{}) for offer task.", tradeWallet.getAccountId(), user.getId());
                    SubmitTransactionResponse rebalanceResponse = sctxBuilder.buildAndSubmit();
                    if(!rebalanceResponse.isSuccess()) {
                        ObjectPair<String, String> errorInfo = StellarConverter.getResultCodesFromExtra(rebalanceResponse);
                        logger.error("Cannot rebalance trade wallet {} {} : {} {}", user.getId(), tradeWallet.getAccountId(), errorInfo.first(), errorInfo.second());
                        throw new TradeWalletRebalanceException(errorInfo.first());
                    }

                    taskRecord.setRebalanceamount(rebalanced.second());
                    taskRecord.setRebalancetxhash(rebalanceResponse.getHash());
                } catch(IOException | AccountRequiresMemoException e) {
                    throw new StellarException(e);
                }
            }
        } catch(TalkenException tex) {
            DexTaskRecord.writeError(taskRecord, position, tex);
            throw tex;
        }
    }

    private void buildTxProcess(StellarChannelTransaction.Builder sctxBuilder, String position, TradeWalletInfo tradeWallet, KeyPair issuerAccount, Asset stakingAssetType, DexTaskStakingRecord taskRecord) throws SigningException {
        try {
            DexTaskTypeEnum taskType = taskRecord.getTasktype();
            if(DexTaskTypeEnum.STAKING.equals(taskType)) {
                sctxBuilder.addOperation(
                        new PaymentOperation
                                .Builder(issuerAccount.getAccountId(), stakingAssetType, StellarConverter.actualToString(taskRecord.getAmount()))
                                .setSourceAccount(tradeWallet.getAccountId())
                                .build()
                )
                        .addSigner(new StellarSignerAccount(twService.extractKeyPair(tradeWallet)));
            } else if(DexTaskTypeEnum.UNSTAKING.equals(taskType)) {
                sctxBuilder.addOperation(
                        new PaymentOperation
                                .Builder(tradeWallet.getAccountId(), stakingAssetType, StellarConverter.actualToString(taskRecord.getAmount()))
                                .setSourceAccount(issuerAccount.getAccountId())
                                .build()
                )
                        .addSigner(new StellarSignerTSS(signServerService, issuerAccount.getAccountId()));
            }

            // TODO : if need to pre-paid fee for staking or unstaking
        } catch(TalkenException tex) {
            DexTaskRecord.writeError(taskRecord, position, tex);
            throw tex;
        }
    }

    private void buildSctxProcess(StellarChannelTransaction.Builder sctxBuilder, String position, DexTaskStakingRecord taskRecord)
            throws StellarException, SigningException {
        SubmitTransactionResponse txResponse;
        // put tx info and submit tx
        try(StellarChannelTransaction sctx = sctxBuilder.build()) {
            taskRecord.setTxSeq(sctx.getTx().getSequenceNumber());
            taskRecord.setTxHash(ByteArrayUtil.toHexString(sctx.getTx().hash()));
            taskRecord.setTxXdr(sctx.getTx().toEnvelopeXdrBase64());
            taskRecord.store();

            position = "submit_sctx";
            txResponse = sctx.submit();

            if(!txResponse.isSuccess()) {
                throw StellarException.from(txResponse);
            }
        } catch(TalkenException tex) {
            DexTaskRecord.writeError(taskRecord, position, tex);
            throw tex;
        } catch(IOException | AccountRequiresMemoException ioex) {
            StellarException ex = new StellarException(ioex);
            DexTaskRecord.writeError(taskRecord, position, ex);
            throw ex;
        }
    }

    private User getUser(long userId) {
        return dslContext.selectFrom(USER)
                .where(USER.ID.eq(userId))
                .fetchAnyInto(User.class);
    }
}
