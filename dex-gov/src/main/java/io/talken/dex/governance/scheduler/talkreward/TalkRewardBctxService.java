package io.talken.dex.governance.scheduler.talkreward;

import io.talken.common.exception.common.TokenMetaNotFoundException;
import io.talken.common.persistence.enums.BctxStatusEnum;
import io.talken.common.persistence.enums.BlockChainPlatformEnum;
import io.talken.common.persistence.enums.TokenMetaAuxCodeEnum;
import io.talken.common.persistence.jooq.tables.records.BctxRecord;
import io.talken.common.persistence.jooq.tables.records.UserRewardRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.collection.ObjectPair;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.GovSettings;
import io.talken.dex.governance.service.TokenMeta;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.governance.service.integration.wallet.TalkenWalletService;
import io.talken.dex.shared.TransactionBlockExecutor;
import io.talken.dex.shared.service.blockchain.luniverse.LuniverseNetworkService;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.utils.Convert;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

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

	@Autowired
	private LuniverseNetworkService lukClient;

	private String distributorAddress;

	private static final String talkSymbol = "TALK";
	private static final int coolDownTxNum = 100;
	private static final BigDecimal minumumLukBalnace = new BigDecimal(200);

	@PostConstruct
	private void init() {
		this.distributorAddress = govSettings.getTalkDistributorAddress();
	}

	@Scheduled(fixedDelay = 60000, initialDelay = 4000)
	private void rewardToBctx() {
		try {
			BigDecimal lukBalance = getDistributorLukBalance();
			if(lukBalance.compareTo(minumumLukBalnace) >= 0) {
				checkRewardAndQueueBctx();
			} else {
				alarmService.warn(logger, "TALK Distributor {} has low LUK balance ({} LUK < {} LUK). User Reward Distribution Halted.", this.distributorAddress, lukBalance.stripTrailingZeros().toPlainString(), minumumLukBalnace.stripTrailingZeros().toPlainString());
			}
		} catch(Exception ex) {
			alarmService.exception(logger, ex);
		}
	}

	private BigDecimal getDistributorLukBalance() throws IOException {
		BigInteger balanceWei = lukClient.newMainRpcClient().ethGetBalance(this.distributorAddress, DefaultBlockParameterName.LATEST).send().getBalance();
		return Convert.fromWei(balanceWei.toString(), Convert.Unit.ETHER);
	}

	private void checkRewardAndQueueBctx() throws TokenMetaNotFoundException, InterruptedException {
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
				// luniverse MainChain tx rate adjustment
				// this is request from lambda256
				if(count > coolDownTxNum) {
					// cool down distribute after 100 txs.
					break;
				} else {
					// slow down tx rate by 100ms between txs.
					Thread.sleep(100);
				}

				UserRewardRecord rewardRecord = rewards.fetchNext();

				String userWalletAddress;

				try {
					ObjectPair<Boolean, String> address = walletService.getAddress(rewardRecord.getUserId(), tm.getPlatform(), tm.getSymbol());
					if(address.first().equals(false)) {
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
						logger.error("User {} wallet does not containes {}(Luniverse) wallet address.");
						rewardRecord.setScheduleTimestamp(UTCUtil.getNow().plusHours(24));
						rewardRecord.store();
						continue;
					}
				} catch(Exception ex) {
					alarmService.exception(logger, ex);
					continue;
				}

				try {
					TransactionBlockExecutor.of(txMgr).transactional(() -> {
						BctxRecord bctxRecord = new BctxRecord();
						bctxRecord.setStatus(BctxStatusEnum.QUEUED);
						bctxRecord.setBctxType(tm.getBctxType());
						bctxRecord.setSymbol(tm.getSymbol());
						bctxRecord.setPlatformAux(tm.getAux().get(TokenMetaAuxCodeEnum.ERC20_CONTRACT_ID).toString());
						bctxRecord.setAddressFrom(this.distributorAddress);
						bctxRecord.setAddressTo(userWalletAddress);
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
				} catch(Exception ex) {
					alarmService.error(logger, "Reward distribute failed : {} {}", ex.getClass().getSimpleName(), ex.getMessage());
					alarmService.exception(logger, ex);
					rewardRecord.setErrorcode(ex.getClass().getSimpleName());
					rewardRecord.setErrormessage(ex.getMessage());
					rewardRecord.setCheckFlag(true);
					rewardRecord.store();
				}
			}

			if(count > 0) {
				alarmService.info(logger, "{} ({} {}) reward distribution tx queued", count, amount.stripTrailingZeros().toPlainString(), talkSymbol);
			}
		}
	}
}