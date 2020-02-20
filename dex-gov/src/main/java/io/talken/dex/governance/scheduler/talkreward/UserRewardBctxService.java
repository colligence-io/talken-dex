package io.talken.dex.governance.scheduler.talkreward;

import io.talken.common.exception.common.IntegrationException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.jooq.tables.records.BctxRecord;
import io.talken.common.persistence.jooq.tables.records.UserRewardRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.ObjectPair;
import io.talken.common.util.collection.SingleKeyTable;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.TransactionBlockExecutor;
import io.talken.dex.shared.exception.TradeWalletCreateFailedException;
import io.talken.dex.shared.exception.TradeWalletRebalanceException;
import io.talken.dex.shared.service.blockchain.stellar.StellarChannelTransaction;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import io.talken.dex.shared.service.integration.wallet.TalkenWalletService;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.talken.common.persistence.jooq.Tables.USER;
import static io.talken.common.persistence.jooq.Tables.USER_REWARD;

@Service
@Scope("singleton")
public class UserRewardBctxService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(UserRewardBctxService.class);

	@Autowired
	private DSLContext dslContext;

	@Autowired
	protected DataSourceTransactionManager txMgr;

	@Autowired
	private AdminAlarmService alarmService;

	@Autowired
	private TokenMetaGovService metaService;

	@Autowired
	private TalkenWalletService pwService;

	@Autowired
	private TradeWalletService twService;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	private static final int tickLimit = 100;

	private boolean isSuspended = false;

	public void suspend() {
		logger.info("UserRewardService SUSPENDED by admin.");
		isSuspended = true;
	}

	public void resume() {
		logger.info("UserRewardService RESUMED by admin.");
		isSuspended = false;
	}

	/**
	 * check reward, queue bctx
	 */
	@Scheduled(fixedDelay = 60000, initialDelay = 4000)
	private void rewardToBctx() {
		if(isSuspended) return;
		if(DexGovStatus.isStopped) return;

		long ts = UTCUtil.getNowTimestamp_s();
		try {
			// do new rewards first
			checkRewardAndQueueBctx(
					dslContext.selectFrom(USER_REWARD)
							.where(USER_REWARD.APPROVEMENT_FLAG.eq(true)
									.and(USER_REWARD.CHECK_FLAG.eq(false))
									.and(USER_REWARD.BCTX_ID.isNull())
									.and(USER_REWARD.SCHEDULE_TIMESTAMP.isNull())
							)
							.limit(tickLimit)
							.fetchLazy(), ts, false
			);

			// check scheduled rewards
			checkRewardAndQueueBctx(
					dslContext.selectFrom(USER_REWARD)
							.where(USER_REWARD.APPROVEMENT_FLAG.eq(true)
									.and(USER_REWARD.CHECK_FLAG.eq(false))
									.and(USER_REWARD.BCTX_ID.isNull())
									.and(USER_REWARD.SCHEDULE_TIMESTAMP.le(UTCUtil.getNow()))
							)
							.limit(tickLimit)
							.fetchLazy(), ts, true
			);
		} catch(Exception ex) {
			alarmService.exception(logger, ex);
		}
	}

	/**
	 * process reward record, queue bctx, update reward record
	 *
	 * @param rewards
	 * @param timestamp
	 * @param isPostponed
	 */
	private void checkRewardAndQueueBctx(Cursor<UserRewardRecord> rewards, long timestamp, boolean isPostponed) {
		SingleKeyTable<String, DistStatus> dStatus = new SingleKeyTable<>();

		Map<String, AtomicInteger> metaMissing = new HashMap<>();

		while(rewards.hasNext()) {
			UserRewardRecord rewardRecord = rewards.fetchNext();

			final String assetCode = rewardRecord.getAssetcode();
			TokenMetaTable.Meta meta;
			try {
				meta = metaService.getTokenMeta(assetCode);
			} catch(TokenMetaNotFoundException ex) {
				if(!metaMissing.containsKey(assetCode)) {
					metaMissing.put(assetCode, new AtomicInteger());
				}
				metaMissing.get(assetCode).incrementAndGet();
				continue;
			}

			try {
				BctxRecord bctxRecord;

				if(rewardRecord.getPrivateWalletFlag()) {
					bctxRecord = getPrivateWalletBctxRecord(rewardRecord, meta);
				} else {
					bctxRecord = processTradeWalletReward(rewardRecord, meta);
				}

				if(bctxRecord != null) {
					TransactionBlockExecutor.of(txMgr).transactional(() -> {
						dslContext.attach(bctxRecord);
						bctxRecord.store();
						rewardRecord.setBctxId(bctxRecord.getId());
						rewardRecord.setCheckFlag(true);
						rewardRecord.store();
					});

					if(!dStatus.has(assetCode)) dStatus.insert(new DistStatus(meta.getSymbol()));
					DistStatus ds = dStatus.select(assetCode);
					ds.getCount().incrementAndGet();
					ds.setAmount(ds.getAmount().add(rewardRecord.getAmount()));
				}
			} catch(Exception ex) {
				alarmService.error(logger, "Reward distribute failed : {} {}", ex.getClass().getSimpleName(), ex.getMessage());
				alarmService.exception(logger, ex);
				rewardRecord.setErrorcode(ex.getClass().getSimpleName());
				rewardRecord.setErrormessage(ex.getMessage());
				rewardRecord.setCheckFlag(true);
				rewardRecord.store();
			}
		}

		for(DistStatus distStatus : dStatus.select()) {
			if(distStatus.getCount().get() > 0) {
				alarmService.info(logger, "Reward Queued [{}]{}: {} {} ({} transaction)", timestamp, isPostponed ? " (Postponed)" : "", distStatus.getAmount().stripTrailingZeros().toPlainString(), distStatus.getAssetCode(), distStatus.getCount().get());
			}
		}
		for(Map.Entry<String, AtomicInteger> missing : metaMissing.entrySet()) {
			alarmService.info(logger, "Reward Error : no meta for {} found. {} rewards is skipped.", missing.getKey(), missing.getValue().get());
		}
	}

	/**
	 * user_reward to bctx record for private wallet
	 *
	 * @param rewardRecord
	 * @param meta
	 * @return
	 */
	private BctxRecord getPrivateWalletBctxRecord(UserRewardRecord rewardRecord, TokenMetaTable.Meta meta) {
		String userWalletAddress;
		String distributorAddress = meta.getManagedInfo().getDistributorAddress();

		if(distributorAddress == null) {
			alarmService.error(logger, "Reward Error : {} distributor address is not set", meta.getSymbol());
			return null;
		}

		try {
			ObjectPair<Boolean, String> address = pwService.getAddress(rewardRecord.getUserId(), meta.getPlatform(), meta.getSymbol());
			if(address.first().equals(false)) {
				logger.debug("User {} does not have valid wallet, postpone reward action for 12 hours", rewardRecord.getUserId());
				// User Wallet not created
				// Postpone reward for 12 hours.
				rewardRecord.setScheduleTimestamp(UTCUtil.getNow().plusHours(12));
				rewardRecord.store();
				return null;
			}

			userWalletAddress = address.second();

			if(userWalletAddress == null) {
				// Luniverse address not found
				// Postpone reward for 24 hours.
				logger.warn("User {} wallet does not containes {} wallet address. postpone reward action for 24 hours", rewardRecord.getUserId(), meta.getSymbol());
				rewardRecord.setScheduleTimestamp(UTCUtil.getNow().plusHours(24));
				rewardRecord.store();
				return null;
			}
		} catch(IntegrationException ex) {
			try {
				logger.warn("Cannot get user wallet : {} {}", ex.getResult().getErrorCode(), ex.getResult().getErrorMessage());
			} catch(Exception ex2) {
				logger.exception(ex2);
			}
			return null;
		} catch(Exception ex) {
			alarmService.exception(logger, ex);
			return null;
		}

		BctxRecord bctxRecord = new BctxRecord();
		bctxRecord.setStatus(BctxStatusEnum.QUEUED);
		bctxRecord.setBctxType(meta.getBctxType());
		bctxRecord.setSymbol(meta.getSymbol());

		switch(meta.getBctxType()) {
			case LUNIVERSE_MAIN_TOKEN:
			case ETHEREUM_ERC20_TOKEN:
				bctxRecord.setPlatformAux(meta.getAux().get(TokenMetaAuxCodeEnum.ERC20_CONTRACT_ID).toString());
				break;
			case STELLAR_TOKEN:
				bctxRecord.setPlatformAux(meta.getAux().get(TokenMetaAuxCodeEnum.STELLAR_ISSUER_ID).toString());
				break;
		}

		bctxRecord.setAddressFrom(meta.getManagedInfo().getDistributorAddress());
		bctxRecord.setAddressTo(userWalletAddress);
		bctxRecord.setAmount(rewardRecord.getAmount());
		bctxRecord.setNetfee(BigDecimal.ZERO);

		return bctxRecord;
	}

	/**
	 * user_reward to bctx record for trade wallet
	 *
	 * @param rewardRecord
	 * @param meta
	 * @return
	 * @throws TradeWalletCreateFailedException
	 * @throws TradeWalletRebalanceException
	 */
	private BctxRecord processTradeWalletReward(UserRewardRecord rewardRecord, TokenMetaTable.Meta meta) throws TradeWalletCreateFailedException, TradeWalletRebalanceException {
		if(!meta.isManaged()) {
			alarmService.error(logger, "Reward Error : {} is not managed asset", meta.getSymbol());
			return null;
		}

		TokenMetaTable.ManagedInfo managedInfo = meta.getManagedInfo();

		User user = dslContext.selectFrom(USER).where(USER.ID.eq(rewardRecord.getUserId())).fetchOneInto(User.class);
		TradeWalletInfo tradeWallet = twService.ensureTradeWallet(user);

		StellarChannelTransaction.Builder sctxBuilder = stellarNetworkService.newChannelTxBuilder();
		try {
			ObjectPair<Boolean, BigDecimal> rebalanced = twService.addNativeBalancingOperation(sctxBuilder, tradeWallet, false, meta.getManagedInfo().getAssetCode());
			if(rebalanced.first()) {
				logger.debug("Rebalance trade wallet {} (#{}) for reward.", tradeWallet.getAccountId(), user.getId());
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
			throw new TradeWalletRebalanceException(ex, "Cannot rebalance trade wallet.");
		}

		BctxRecord bctxRecord = new BctxRecord();
		bctxRecord.setStatus(BctxStatusEnum.QUEUED);
		bctxRecord.setBctxType(BlockChainPlatformEnum.STELLAR_TOKEN);
		bctxRecord.setSymbol(meta.getManagedInfo().getAssetCode());
		bctxRecord.setPlatformAux(meta.getManagedInfo().getIssuerAddress());
		bctxRecord.setAddressFrom(meta.getManagedInfo().getIssuerAddress());
		bctxRecord.setAddressTo(tradeWallet.getAccountId());
		bctxRecord.setAmount(rewardRecord.getAmount());
		bctxRecord.setNetfee(BigDecimal.ZERO);

		String rcode = rewardRecord.getRCode().toLowerCase();

		DexTaskId taskId;

		if(rcode.startsWith("event_")) {
			taskId = DexTaskId.generate_taskId(DexTaskTypeEnum.EVENT);
		} else if(rcode.startsWith("airdrop_")) {
			taskId = DexTaskId.generate_taskId(DexTaskTypeEnum.AIRDROP);
		} else {
			if(rewardRecord.getCsFlag()) {
				taskId = DexTaskId.generate_taskId(DexTaskTypeEnum.CUSTOMER_SERVICE);
			} else {
				taskId = DexTaskId.generate_taskId(DexTaskTypeEnum.REWARD);
			}
		}

		bctxRecord.setTxAux(taskId.getId());

		return bctxRecord;
	}
}