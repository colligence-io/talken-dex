package io.talken.dex.governance.scheduler;

import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.shared.service.tradewallet.TradeWalletService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

import static io.talken.common.persistence.jooq.Tables.USER;
import static io.talken.common.persistence.jooq.Tables.USER_TRADE_WALLET;

@Service
@Scope("singleton")
public class TradeWalletResetService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TradeWalletResetService.class);

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private TradeWalletService twService;

	@Autowired
	private AdminAlarmService adminAlarmService;

	/**
	 * Reset trade wallet if activationHash is marked as 'RESET'
	 */

	// FIXME : RESET is too dangerous, enable this service ONLY IF RESET NEEDED
	//	@Scheduled(fixedDelay = 60000, initialDelay = 4000)
	private void checkToReset() {
		if(DexGovStatus.isStopped) return;

		List<User> usersToReset = dslContext.select(USER.asterisk())
				.from(USER.leftOuterJoin(USER_TRADE_WALLET).on(USER.ID.eq(USER_TRADE_WALLET.USER_ID)))
				.where(USER_TRADE_WALLET.ACTIVATIONTXHASH.eq("RESET"))
				.fetchInto(User.class);

		for(User user : usersToReset) {
			try {
				if(twService.resetTradeWallet(user)) {
					adminAlarmService.info(logger, "Reset user {} trade wallet : success", user.getUid());
				} else {
					adminAlarmService.error(logger, "Reset user {} trade wallet : failed", user.getUid());
				}
			} catch(Exception ex) {
				adminAlarmService.exception(logger, ex, "Cannot reset user {} trade wallet.", user.getUid());
			}
		}
	}
}
