package io.talken.dex.api.service;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import io.talken.common.exception.TalkenException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.exception.common.TokenMetaNotManagedException;
import io.talken.common.persistence.DexTaskRecord;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.jooq.tables.records.DexTaskStakingRecord;
import io.talken.common.persistence.jooq.tables.records.StakingEventRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.ObjectPair;
import io.talken.dex.api.controller.dto.CreateStakingRequest;
import io.talken.dex.api.controller.dto.CreateStakingResult;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.exception.*;
import io.talken.dex.shared.service.blockchain.stellar.*;
import io.talken.dex.shared.service.integration.signer.SignServerService;
import io.talken.dex.shared.service.tradewallet.TradeWalletInfo;
import io.talken.dex.shared.service.tradewallet.TradeWalletService;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.stellar.sdk.AccountRequiresMemoException;
import org.stellar.sdk.Asset;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.PaymentOperation;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_STAKING;
import static io.talken.common.persistence.jooq.Tables.STAKING_EVENT;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.sum;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class StakingService {
    private static final PrefixedLogger logger = PrefixedLogger.getLogger(StakingService.class);

    private final DSLContext dslContext;
    private final TradeWalletService twService;
    private final StellarNetworkService stellarNetworkService;
    private final TokenMetaService tmService;
    private final SignServerService signServerService;

    public CreateStakingResult createStaking(User user, CreateStakingRequest request)
            throws TokenMetaNotFoundException, SigningException, StellarException, TradeWalletCreateFailedException,
            TradeWalletRebalanceException, TokenMetaNotManagedException, StakingEventNotFoundException,
            StakingBalanceNotEnoughException, StakingAmountEnoughException, StakingBeforeStartException,
            UnStakingBeforeExpireException, UnStakingAfterStakingException, StakingUserEnoughException,
            StakingAlreadyExistsException, StakingAfterEndException, StakingTooLittleAmountException,
            UnStakingDisabledException, StakingTooMuchAmountException, StakingTooOverAmountException, UnStakingTooOverAmountException {

        return createStaking(user, true, request);
    }

    public CreateStakingResult createUnStaking(User user, CreateStakingRequest request)
            throws TokenMetaNotFoundException, SigningException, StellarException, TradeWalletCreateFailedException,
            TradeWalletRebalanceException, TokenMetaNotManagedException, StakingEventNotFoundException,
            StakingBalanceNotEnoughException, StakingAmountEnoughException, StakingBeforeStartException,
            UnStakingBeforeExpireException, UnStakingAfterStakingException, StakingUserEnoughException,
            StakingAlreadyExistsException, StakingAfterEndException, StakingTooLittleAmountException,
            UnStakingDisabledException, StakingTooMuchAmountException, StakingTooOverAmountException, UnStakingTooOverAmountException {

        return createStaking(user, false, request);
    }

    private CreateStakingResult createStaking(User user, boolean isStaking, CreateStakingRequest request)
            throws TokenMetaNotFoundException, SigningException, StellarException, TradeWalletCreateFailedException, TradeWalletRebalanceException, TokenMetaNotManagedException,
            StakingEventNotFoundException, StakingBalanceNotEnoughException, UnStakingBeforeExpireException,
            StakingAmountEnoughException, StakingBeforeStartException, StakingAlreadyExistsException,
            UnStakingAfterStakingException, StakingUserEnoughException, StakingAfterEndException,
            StakingTooMuchAmountException, UnStakingDisabledException, StakingTooLittleAmountException, StakingTooOverAmountException, UnStakingTooOverAmountException {
        final DexTaskTypeEnum taskType = (isStaking) ? DexTaskTypeEnum.STAKING : DexTaskTypeEnum.UNSTAKING;
        final DexTaskId dexTaskId = DexTaskId.generate_taskId(taskType);
        final TradeWalletInfo tradeWallet = twService.ensureTradeWallet(user);

        final long userId = user.getId();
        final BigDecimal stakingAmount = StellarConverter.scale(request.getAmount());

        final String stakingEventCode = request.getStakingCode();
        final String stakingEventAssetCode = request.getStakingAssetCode();

        final KeyPair issuerAccount = tmService.getManagedInfo(stakingEventAssetCode).dexIssuerAccount();
        final Asset stakingAssetType = tmService.getAssetType(stakingEventAssetCode);

        BigDecimal userAssetBalance = tradeWallet.getBalance(stakingAssetType);
        StakingEventRecord stakingEventRecord = checkAvailable(userId, stakingEventCode, stakingEventAssetCode, userAssetBalance, stakingAmount, isStaking);

        String position;

        DexTaskStakingRecord taskRecord = new DexTaskStakingRecord();
        taskRecord.setTaskid(dexTaskId.getId());
        taskRecord.setUserId(userId);
        taskRecord.setTasktype(taskType);
        taskRecord.setTradeaddr(tradeWallet.getAccountId());
        taskRecord.setHolderaddr(stakingEventRecord.getHolderaddr());
        taskRecord.setStakingEventId(stakingEventRecord.getId());
        taskRecord.setAssetcode(stakingEventAssetCode);
        taskRecord.setAmount(stakingAmount);
        dslContext.attach(taskRecord);
        taskRecord.store();
        logger.info("{} generated. userId = {}", dexTaskId, userId);

        // Adjust native balance before offer
        position = "rebalance";
        try {
            StellarChannelTransaction.Builder sctxBuilder = stellarNetworkService.newChannelTxBuilder();

            ObjectPair<Boolean, BigDecimal> rebalanced;
            rebalanced = twService.addNativeBalancingOperation(sctxBuilder, tradeWallet, true, stakingEventAssetCode);

            if(rebalanced.first()) {
                try {
                    logger.debug("Rebalance trade wallet {} (#{}) for offer task.", tradeWallet.getAccountId(), userId);
                    SubmitTransactionResponse rebalanceResponse = sctxBuilder.buildAndSubmit();
                    if(!rebalanceResponse.isSuccess()) {
                        ObjectPair<String, String> errorInfo = StellarConverter.getResultCodesFromExtra(rebalanceResponse);
                        logger.error("Cannot rebalance trade wallet {} {} : {} {}", user.getId(), tradeWallet.getAccountId(), errorInfo.first(), errorInfo.second());
                        throw new TradeWalletRebalanceException(errorInfo.first());
                    }

                    taskRecord.setRebalanceamount(rebalanced.second());
                    taskRecord.setRebalancetxhash(rebalanceResponse.getHash());
                    taskRecord.store();
                } catch(IOException | AccountRequiresMemoException e) {
                    throw new StellarException(e);
                }
            }
        } catch(TalkenException tex) {
            DexTaskRecord.writeError(taskRecord, position, tex);
            throw tex;
        }

        position = "build_tx";
        StellarChannelTransaction.Builder sctxBuilder;
        try {
            sctxBuilder = stellarNetworkService.newChannelTxBuilder()
                    .setMemo(dexTaskId.getId());

            if(isStaking) {
                sctxBuilder.addOperation(
                        new PaymentOperation
                                .Builder(issuerAccount.getAccountId(), stakingAssetType, StellarConverter.actualToString(stakingAmount))
                                .setSourceAccount(tradeWallet.getAccountId())
                                .build()
                )
                .addSigner(new StellarSignerAccount(twService.extractKeyPair(tradeWallet)));
            } else {
                sctxBuilder.addOperation(
                        new PaymentOperation
                                .Builder(tradeWallet.getAccountId(), stakingAssetType, StellarConverter.actualToString(stakingAmount))
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

        position = "build_sctx";
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

        logger.info("{} complete. userId = {}", dexTaskId, userId);

        CreateStakingResult result = new CreateStakingResult();
        result.setTaskId(dexTaskId.getId());
        result.setTaskType(taskType);
        result.setStakingCode(stakingEventCode);
        result.setStakingAssetCode(stakingEventAssetCode);
        result.setAmount(stakingAmount);
        return result;
    }

    public boolean checkAvailable(User user, CreateStakingRequest request) {
//        final TradeWalletInfo tradeWallet = twService.ensureTradeWallet(user);
//        final BigDecimal stakingAmount = StellarConverter.scale(request.getAmount());
        final String stakingEventCode = request.getStakingCode();
        final String stakingEventAssetCode = request.getStakingAssetCode();
//        final Asset stakingAssetType = tmService.getAssetType(stakingEventAssetCode);
//        BigDecimal userAssetBalance = tradeWallet.getBalance(stakingAssetType);

        StakingEventRecord stakingEventRecord = dslContext.selectFrom(STAKING_EVENT)
                .where(STAKING_EVENT.STAKING_CODE.eq(stakingEventCode)
                        .and(STAKING_EVENT.ASSET_CODE.eq(stakingEventAssetCode)))
                .fetchAny();

        // Staking Event Check
        if (stakingEventRecord == null) return false;

        long stakingEventId = stakingEventRecord.getId();
        // 모집금액 제약
        BigDecimal totalAmountLimit = stakingEventRecord.getTotalAmountLimit();
        BigDecimal sumUserAmount = dslContext.select(sum(DEX_TASK_STAKING.AMOUNT))
                .from(DEX_TASK_STAKING)
                .where(DEX_TASK_STAKING.STAKING_EVENT_ID.eq(stakingEventId)
                        .and(DEX_TASK_STAKING.ASSETCODE.eq(stakingEventAssetCode))
                        .and(DEX_TASK_STAKING.TASKTYPE.eq(DexTaskTypeEnum.STAKING))
                )
                .fetchOneInto(BigDecimal.class);
        if (sumUserAmount == null) sumUserAmount = BigDecimal.ZERO;

        if (totalAmountLimit.compareTo(BigDecimal.ZERO) > 0 && totalAmountLimit.compareTo(sumUserAmount) <= 0)
            return false;
        return true;
//        return checkAvailable(user.getId(), request.getStakingCode(), request.getStakingAssetCode(), userAssetBalance, stakingAmount, true) > 0;
    }

    private StakingEventRecord checkAvailable(long userId, String stakingEventCode, String stakingEventAssetCode, BigDecimal userAssetBalance, BigDecimal stakingAmount, boolean isStaking)
            throws StakingEventNotFoundException, StakingAmountEnoughException, StakingUserEnoughException, StakingAlreadyExistsException, UnStakingAfterStakingException, UnStakingBeforeExpireException, StakingAfterEndException, StakingBeforeStartException, StakingBalanceNotEnoughException, StakingTooLittleAmountException, StakingTooMuchAmountException, UnStakingDisabledException, StakingTooOverAmountException, UnStakingTooOverAmountException {
        StakingEventRecord stakingEventRecord = dslContext.selectFrom(STAKING_EVENT)
                .where(STAKING_EVENT.STAKING_CODE.eq(stakingEventCode)
                        .and(STAKING_EVENT.ASSET_CODE.eq(stakingEventAssetCode)))
                .fetchAny();

        // Staking Event Check
        if (stakingEventRecord == null) throw new StakingEventNotFoundException(stakingEventCode, stakingEventAssetCode);

        long stakingEventId = stakingEventRecord.getId();

        // 참여 금액 제약
        if (userAssetBalance.compareTo(stakingAmount) < 0)
            throw new StakingBalanceNotEnoughException(userAssetBalance, stakingAmount);
        // 참여 금액 제약(min)
        final BigDecimal minUserAmountLimit = stakingEventRecord.getMinUserAmountLimit();
        if (minUserAmountLimit != null && stakingAmount.compareTo(minUserAmountLimit) < 0)
            throw new StakingTooLittleAmountException(minUserAmountLimit, stakingAmount);
        // 참여 금액 제약(max)
        final BigDecimal maxUserAmountLimit = stakingEventRecord.getMaxUserAmountLimit();
        if (maxUserAmountLimit != null && stakingAmount.compareTo(maxUserAmountLimit) > 0)
            throw new StakingTooMuchAmountException(maxUserAmountLimit, stakingAmount);

        // 모집금액 제약
        BigDecimal totalAmountLimit = stakingEventRecord.getTotalAmountLimit();
        BigDecimal sumUserAmount = dslContext.select(sum(DEX_TASK_STAKING.AMOUNT))
                .from(DEX_TASK_STAKING)
                .where(DEX_TASK_STAKING.STAKING_EVENT_ID.eq(stakingEventId)
                        .and(DEX_TASK_STAKING.ASSETCODE.eq(stakingEventAssetCode))
                        .and(DEX_TASK_STAKING.TASKTYPE.eq(DexTaskTypeEnum.STAKING))
                )
                .fetchOneInto(BigDecimal.class);
        if (sumUserAmount == null) sumUserAmount = BigDecimal.ZERO;

        if (totalAmountLimit.compareTo(BigDecimal.ZERO) > 0 && totalAmountLimit.compareTo(sumUserAmount) <= 0)
            throw new StakingAmountEnoughException(totalAmountLimit, sumUserAmount);

        // 모집인원 제약
        int totalUserLimit = stakingEventRecord.getTotalUserLimit();
        Integer sumUserCount = dslContext.select(count(DEX_TASK_STAKING.ID))
                .from(DEX_TASK_STAKING)
                .where(DEX_TASK_STAKING.STAKING_EVENT_ID.eq(stakingEventId)
                        .and(DEX_TASK_STAKING.ASSETCODE.eq(stakingEventAssetCode))
                        .and(DEX_TASK_STAKING.TASKTYPE.eq(DexTaskTypeEnum.STAKING))
                )
                .fetchOneInto(Integer.class);
        if (sumUserCount == null) sumUserCount = 0;

        if (totalUserLimit > 0 && totalUserLimit <= sumUserCount)
            throw new StakingUserEnoughException(totalUserLimit, sumUserCount);

        BigDecimal sumUserTotalStakingAmount = dslContext.select(sum(DEX_TASK_STAKING.AMOUNT))
                .from(DEX_TASK_STAKING)
                .where(DEX_TASK_STAKING.STAKING_EVENT_ID.eq(stakingEventId)
                        .and(DEX_TASK_STAKING.ASSETCODE.eq(stakingEventAssetCode))
                        .and(DEX_TASK_STAKING.TASKTYPE.eq(DexTaskTypeEnum.STAKING))
                        .and(DEX_TASK_STAKING.USER_ID.eq(userId))
                )
                .fetchOneInto(BigDecimal.class);
        if (sumUserTotalStakingAmount == null) sumUserTotalStakingAmount = BigDecimal.ZERO;

        BigDecimal sumUserTotalUnStakingAmount = dslContext.selectFrom(DEX_TASK_STAKING)
                .where(DEX_TASK_STAKING.STAKING_EVENT_ID.eq(stakingEventId)
                        .and(DEX_TASK_STAKING.ASSETCODE.eq(stakingEventAssetCode))
                        .and(DEX_TASK_STAKING.TASKTYPE.eq(DexTaskTypeEnum.UNSTAKING))
                        .and(DEX_TASK_STAKING.USER_ID.eq(userId))
                )
                .fetchOneInto(BigDecimal.class);
        if (sumUserTotalUnStakingAmount == null) sumUserTotalUnStakingAmount = BigDecimal.ZERO;

        BigDecimal currentTotalStakingAmount = sumUserTotalStakingAmount.subtract(sumUserTotalUnStakingAmount);

        // staking 제약
        if (isStaking) {
            Boolean dupParticipate = stakingEventRecord.getDupParticipate();
            // 이미 참여
            if (dupParticipate.equals(Boolean.FALSE) && sumUserTotalStakingAmount.compareTo(BigDecimal.ZERO) > 0)
                throw new StakingAlreadyExistsException(stakingEventCode, stakingEventAssetCode);
            // 참여 금액 오버
            if (totalAmountLimit.compareTo(sumUserAmount.add(currentTotalStakingAmount)) < 0)
                throw new StakingTooOverAmountException(totalAmountLimit.subtract(sumUserAmount.add(currentTotalStakingAmount)));
        }

        // unstaking 제약
        LocalDateTime expr = stakingEventRecord.getExprTimestamp();
        if (!isStaking) {
            Boolean unStakingFlag = stakingEventRecord.getUserUnstakingFlag();
            if (unStakingFlag.equals(Boolean.FALSE))
                throw new UnStakingDisabledException(stakingEventCode, stakingEventAssetCode);
            else {
                // 참여 없음 취소 불가
                if (sumUserTotalStakingAmount.compareTo(BigDecimal.ZERO) <= 0)
                    throw new UnStakingAfterStakingException(stakingEventCode, stakingEventAssetCode);
                //
                if (stakingAmount.compareTo(currentTotalStakingAmount) > 0)
                    throw new UnStakingTooOverAmountException(currentTotalStakingAmount.subtract(stakingAmount));
                // 만료 이전 취소 불가
                if (expr != null && UTCUtil.getNow().isBefore(expr))
                    throw new UnStakingBeforeExpireException(stakingEventCode, stakingEventAssetCode, expr);
            }
        }

        // 기간 제약
        LocalDateTime start = stakingEventRecord.getStartTimestamp();
        LocalDateTime end = stakingEventRecord.getEndTimestamp();

        if (UTCUtil.getNow().isBefore(start)) throw new StakingBeforeStartException(stakingEventCode, stakingEventAssetCode, start);
        else if (UTCUtil.getNow().isAfter(end)) throw new StakingAfterEndException(stakingEventCode, stakingEventAssetCode, end);

        return stakingEventRecord;
    }
}
