package io.talken.dex.governance.scheduler.talkreward;

import io.talken.common.exception.common.RestApiErrorException;
import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.persistence.jooq.tables.records.BctxRecord;
import io.talken.common.persistence.jooq.tables.records.UserRewardRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.ObjectPair;
import io.talken.dex.governance.GovSettings;
import io.talken.dex.governance.service.AdminAlarmService;
import io.talken.dex.governance.service.TokenMeta;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.governance.service.integration.wallet.TalkenWalletService;
import io.talken.dex.shared.TransactionBlockExecutor;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import static io.talken.common.persistence.jooq.Tables.USER_REWARD;

@Service
@Scope("singleton")
public class TalkRewardBctxService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TalkRewardBctxService.class);

	@Autowired
	private GovSettings govSettings;

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

	private static final String talkSymbol = "TALK";

	@Scheduled(fixedDelay = 60000, initialDelay = 4000)
	private void rewardToBctx() {
		try {
			checkRewardAndQueueBctx();
		} catch(Exception ex) {
			alarmService.exception(logger, ex);
		}
	}

	private void checkRewardAndQueueBctx() throws TokenMetaNotFoundException {
		// ensure TALK meta is on TMS
		TokenMeta tm = metaService.getMeta(talkSymbol);
		// FIXME : HARD CODED, LUNIVERSE_MT ONLY FOR REWARD
		if(!tm.getBctxType().equals(BlockChainPlatformEnum.LUNIVERSE_MAIN_TOKEN)) {
			alarmService.error(logger, "cannot handle {} reward type.", tm.getBctxType());
			return;
		}

		Cursor<UserRewardRecord> rewards = dslContext.selectFrom(USER_REWARD)
				.where(USER_REWARD.APPROVEMENT_FLAG.eq(true)
						.and(USER_REWARD.CHECK_FLAG.eq(false))
						.and(USER_REWARD.BCTX_ID.isNull())
						.and(USER_REWARD.SCHEDULE_TIMESTAMP.isNull()
								.or(USER_REWARD.SCHEDULE_TIMESTAMP.le(UTCUtil.getNow()))
						)
				).fetchLazy();

		if(rewards.hasNext()) {
			int count = 0;
			BigDecimal amount = BigDecimal.ZERO;

			while(rewards.hasNext()) {
				UserRewardRecord rewardRecord = rewards.fetchNext();
				try {
					ObjectPair<Boolean, String> address = walletService.getAddress(rewardRecord.getUserId(), tm.getPlatform(), tm.getSymbol());

					if(address.first().equals(true)) {
						if(address.second() != null) {
							TransactionBlockExecutor.of(txMgr).transactional(() -> {
								BctxRecord bctxRecord = new BctxRecord();
								bctxRecord.setStatus(BctxStatusEnum.QUEUED);
								bctxRecord.setBctxType(tm.getBctxType());
								bctxRecord.setSymbol(tm.getSymbol());
								bctxRecord.setPlatformAux(tm.getAux().get(TokenMetaAuxCodeEnum.ERC20_CONTRACT_ID).toString());
								bctxRecord.setAddressFrom(govSettings.getTalkDistributorAddress());
								bctxRecord.setAddressTo(address.second());
								bctxRecord.setAmount(rewardRecord.getAmount());
								bctxRecord.setNetfee(BigDecimal.ZERO);
								dslContext.attach(bctxRecord);
								bctxRecord.store();
								rewardRecord.setBctxId(bctxRecord.getId());
								rewardRecord.setCheckFlag(true);
								rewardRecord.store();
							});
							count++;
							amount = amount.add(rewardRecord.getAmount());
						} else {
							alarmService.error(logger, "Cannot find {} address for user #{}", talkSymbol, rewardRecord.getUserId());
							rewardRecord.setErrorcode("CannotFindAddress");
							rewardRecord.setErrormessage("Cannot find " + talkSymbol + " address from wallet-api response, userId = " + rewardRecord.getUserId());
							rewardRecord.setCheckFlag(true);
							rewardRecord.store();
						}
					}
				} catch(RestApiErrorException ex) {
					alarmService.error(logger, "Reward distribute failed (Wallet-RestApiFailed) : {} {}", ex.getApiResult().getErrorCode(), ex.getApiResult().getErrorMessage());
					logger.exception(ex);
					rewardRecord.setErrorcode(ex.getApiResult().getErrorCode());
					rewardRecord.setErrormessage(ex.getApiResult().getErrorMessage());
					rewardRecord.setCheckFlag(true);
					rewardRecord.store();
				} catch(Exception ex) {
					alarmService.error(logger, "Reward distribute failed : {} {}", ex.getClass().getSimpleName(), ex.getMessage());
					alarmService.exception(logger, ex);
					rewardRecord.setErrorcode(ex.getClass().getSimpleName());
					rewardRecord.setErrormessage(ex.getMessage());
					rewardRecord.setCheckFlag(true);
					rewardRecord.store();
				}
			}

			alarmService.info(logger, "{} ({} {}) reward distribution tx queued", count, amount.stripTrailingZeros().toPlainString(), talkSymbol);
		}
	}
}