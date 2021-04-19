package io.talken.dex.governance.service;

import io.talken.common.persistence.jooq.tables.pojos.User;
import io.talken.common.persistence.redis.RedisConsts;
import io.talken.common.util.PostLaunchExecutor;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.scheduler.talkreward.UserRewardBctxService;
import io.talken.dex.governance.service.management.MaMonitorService;
import io.talken.dex.governance.service.management.NodeMonitorService;
import io.talken.dex.shared.TokenMetaTable;
import io.talken.dex.shared.TokenMetaTableUpdateEventHandler;
import io.talken.dex.shared.service.tradewallet.TradeWalletService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static io.talken.common.persistence.jooq.Tables.USER;

@Service
public class AdminRedisMessageListnerService implements MessageListener {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AdminRedisMessageListnerService.class);

	private static final String SVC = "tkn-dex-gov";

	private TokenMetaTable tmTable = new TokenMetaTable();
	private TokenMetaTable miTable = new TokenMetaTable();
	private List<TokenMetaTableUpdateEventHandler> updateHandlers = null;

	@Autowired
	private RedisMessageListenerContainer container;

	@Autowired
	private UserRewardBctxService userRewardBctxService;

	@Autowired
	private TradeWalletService twService;

	@Autowired
	private AdminAlarmService adminAlarmService;

	@Autowired
	private MaMonitorService mamService;

	@Autowired
	private NodeMonitorService nmService;

	@Autowired
	private DSLContext dslContext;

	@PostConstruct
	private void init() {
		PostLaunchExecutor.addTask(() ->
				container.addMessageListener(this, new ChannelTopic(RedisConsts.KEY_GOVERNANCE_PUBSUB))
		);
	}

	@Override
	public void onMessage(Message message, byte[] pattern) {
		final String msg = new String(message.getBody(), StandardCharsets.UTF_8).replaceAll("^\"", "").replaceAll("\"$", "");
		if(!msg.toLowerCase().startsWith(SVC)) return;
		final String command = msg.substring(SVC.length()).trim();

		logger.info("Admin command received via channel {} : {}", new String(pattern), command);

		switch(command.toLowerCase()) {
			case "stop service userreward":
				userRewardBctxService.suspend();
				adminAlarmService.warn(logger, "UserReward Service SUSPENDED.");
				break;
			case "start service userreward":
				userRewardBctxService.resume();
				adminAlarmService.warn(logger, "UserReward Service RESUMED.");
				break;
			case "stop service mam":
				mamService.suspend();
				adminAlarmService.warn(logger, "MaMonitor Service SUSPENDED.");
				break;
			case "start service mam":
				mamService.resume();
				adminAlarmService.warn(logger, "MaMonitor Service RESUMED.");
				break;
			case "stop service all":
				DexGovStatus.isStopped = true;
				adminAlarmService.warn(logger, "Dex Governance Service SUSPENDED.");
				break;
			case "start service all":
				DexGovStatus.isStopped = false;
				adminAlarmService.warn(logger, "Dex Governance Service RESUMED.");
				break;
			case "node status":
				StringBuilder sb = nmService.alarmNodeStatus(UTCUtil.getNow());
				adminAlarmService.info(logger, sb.toString());
				break;
		}

		if(command.startsWith("rebalance trade wallet ")) {
			rebalanceTradeWallet(command.replaceFirst("rebalance trade wallet ", ""));
		}

		if(command.startsWith("reset trade wallet ")) {
			resetTradeWallet(command.replaceFirst("reset trade wallet ", ""));
		}

		if(command.startsWith("update storage")) {
			updateStorage(tmTable, miTable);
		}
	}

	private void rebalanceTradeWallet(String cmd) {
		String[] args = cmd.split(" ");

		if(args.length < 3)
			adminAlarmService.warn(logger, "ADMIN TRADE WALLET REBALANCE : not enough args, usage = rebalance trade wallet [UID] [ASSETCODE] [TARGET BALANCE]");

		User u = dslContext.selectFrom(USER).where(USER.UID.eq(args[0])).fetchOneInto(User.class);

		if(u == null)
			adminAlarmService.warn(logger, "ADMIN TRADE WALLET REBALANCE : user " + args[0] + " not found");

		try {
			String txHash = twService.rebalanceIssuedAsset(u, args[1], new BigDecimal(args[2]));
			adminAlarmService.info(logger, "ADMIN TRADE WALLET REBALANCE : " + args[0] + " " + args[1] + " " + args[2] + " : " + txHash);
		} catch(Exception ex) {
			adminAlarmService.error(logger, "ADMIN TRADE WALLET REBALANCE : Exception :: {}", ex.getClass().getSimpleName() + " " + ex.getMessage());
		}
	}

	private void resetTradeWallet(String cmd) {
		String[] args = cmd.split(" ");

		if(args.length < 1)
			adminAlarmService.warn(logger, "ADMIN TRADE WALLET RESET : not enough args, usage = reset trade wallet [UID]");

		User u = dslContext.selectFrom(USER).where(USER.UID.eq(args[0])).fetchOneInto(User.class);

		if(u == null)
			adminAlarmService.warn(logger, "ADMIN TRADE WALLET RESET : user " + args[0] + " not found");

		try {
			boolean result = twService.resetTradeWallet(u);
			adminAlarmService.info(logger, "ADMIN TRADE WALLET RESET : " + args[0] + " " + result);
		} catch(Exception ex) {
			adminAlarmService.error(logger, "ADMIN TRADE WALLET RESET : Exception :: {}", ex.getClass().getSimpleName() + " " + ex.getMessage());
		}
	}

	private void updateStorage(TokenMetaTable tmTable, TokenMetaTable miTable) {
		try {
			logger.info("Executing admin command : Update Token Meta storage.");
			TokenMetaTable newMiTable = new TokenMetaTable();

			for(Map.Entry<String, TokenMetaTable.Meta> _kv : tmTable.entrySet()) {
				if(_kv.getValue().isManaged()) {
					_kv.getValue().getManagedInfo().prepareCache();
					newMiTable.put(_kv.getKey(), _kv.getValue());
				}
			}

			if(updateHandlers != null) {
				for(TokenMetaTableUpdateEventHandler updateHandler : updateHandlers) {
					try {
						updateHandler.handleTokenMetaTableUpdate(tmTable);
					} catch(Exception ex) {
						logger.exception(ex, "Exception detected while updating meta data. this may cause unpredictable results.");
					}
				}
			}

			this.tmTable = tmTable;
			this.miTable = newMiTable;

			logger.info("Token Meta loaded : all {}, managed {}", tmTable.size(), miTable.size());
			adminAlarmService.info(logger, "ADMIN UPDATE TOKEN META STORAGE.");
		} catch(Exception ex) {
			adminAlarmService.error(logger, "EXCEPTION DETECTED : while updating meta data. this may cause unpredictable results. :: {}", ex);
		}
	}
}
