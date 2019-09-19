package io.talken.dex.governance.scheduler.talkreward;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.persistence.jooq.tables.records.BctxRecord;
import io.talken.common.persistence.jooq.tables.records.UserRewardRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.ObjectPair;
import io.talken.common.util.collection.SingleKeyTable;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.service.TokenMeta;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.shared.TransactionBlockExecutor;
import io.talken.dex.shared.service.integration.wallet.TalkenWalletService;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;

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
	private TalkenWalletService walletService;

	private static final int tickLimit = 100;

	@PostConstruct
	private void init() {
	}

	@Scheduled(fixedDelay = 60000, initialDelay = 4000)
	private void rewardToBctx() {
		try {
			checkRewardAndQueueBctx();
		} catch(Exception ex) {
			alarmService.exception(logger, ex);
		}
	}

	private DistStatus createDistStatus(TokenMeta meta) {
		DistStatus ds = new DistStatus(meta.getSymbol());
		ds.setDistributorAddress(meta.getManagedInfo().getDistributoraddress());
		return ds;
	}

	private void checkRewardAndQueueBctx() throws TokenMetaNotFoundException {
		SingleKeyTable<String, DistStatus> dStatus = new SingleKeyTable<>();

		Cursor<UserRewardRecord> rewards = dslContext.selectFrom(USER_REWARD)
				.where(USER_REWARD.APPROVEMENT_FLAG.eq(true)
						.and(USER_REWARD.CHECK_FLAG.eq(false))
						.and(USER_REWARD.BCTX_ID.isNull())
						.and(USER_REWARD.SCHEDULE_TIMESTAMP.isNull()
								.or(USER_REWARD.SCHEDULE_TIMESTAMP.le(UTCUtil.getNow()))
						)
				)
				.limit(tickLimit)
				.fetchLazy();

		while(rewards.hasNext()) {
			UserRewardRecord rewardRecord = rewards.fetchNext();

			final String assetCode = rewardRecord.getAssetcode();
			TokenMeta meta = metaService.getMeta(assetCode);

			if(!dStatus.has(assetCode)) dStatus.insert(createDistStatus(meta));

			DistStatus ds = dStatus.select(assetCode);

			if(ds.getDistributorAddress() == null || ds.getDistributorAddress().isEmpty())
				continue; // skip distribution if no distributor set

			String userWalletAddress;

			try {
				ObjectPair<Boolean, String> address = walletService.getAddress(rewardRecord.getUserId(), meta.getPlatform(), meta.getSymbol());
				if(address.first().equals(false)) {
					logger.debug("User {} does not have valid wallet, postpone reward action for 12 hours", rewardRecord.getUserId());
					// User Wallet not created
					// Postpone reward for 12 hours.
					rewardRecord.setScheduleTimestamp(UTCUtil.getNow().plusHours(12));
					rewardRecord.store();
					continue;
				}

				userWalletAddress = address.second();

				if(userWalletAddress == null) {
					// Luniverse address not found
					// Postpone reward for 24 hours.
					logger.warn("User {} wallet does not containes {} wallet address. postpone reward action for 24 hours", rewardRecord.getUserId(), meta.getSymbol());
					rewardRecord.setScheduleTimestamp(UTCUtil.getNow().plusHours(24));
					rewardRecord.store();
					continue;
				}
			} catch(Exception ex) {
				alarmService.exception(logger, ex);
				continue;
			}

			try {
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

				bctxRecord.setAddressFrom(ds.getDistributorAddress());
				bctxRecord.setAddressTo(userWalletAddress);
				bctxRecord.setAmount(rewardRecord.getAmount());
				bctxRecord.setNetfee(BigDecimal.ZERO);

				TransactionBlockExecutor.of(txMgr).transactional(() -> {
					dslContext.attach(bctxRecord);
					bctxRecord.store();
					rewardRecord.setBctxId(bctxRecord.getId());
					rewardRecord.setCheckFlag(true);
					rewardRecord.store();
				});
				ds.getCount().incrementAndGet();
				ds.setAmount(ds.getAmount().add(rewardRecord.getAmount()));
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
			if(distStatus.getDistributorAddress() == null || distStatus.getDistributorAddress().isEmpty()) {
				alarmService.info(logger, "Reward Error : {} distributor address is not set", distStatus.getAssetCode());
			}

			if(distStatus.getCount().get() > 0) {
				alarmService.info(logger, "Reward Queued : {} {} ({} transaction)", distStatus.getAmount().stripTrailingZeros().toPlainString(), distStatus.getAssetCode(), distStatus.getCount().get());
			}
		}
	}
}