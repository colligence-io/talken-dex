package io.talken.dex.governance.service;

import io.talken.common.persistence.redis.RedisConsts;
import io.talken.common.util.PostLaunchExecutor;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.DexGovStatus;
import io.talken.dex.governance.scheduler.talkreward.UserRewardBctxService;
import io.talken.dex.governance.service.mam.MaMonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

@Service
public class AdminRedisMessageListnerService implements MessageListener {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(AdminRedisMessageListnerService.class);

	private static final String SVC = "tkn-dex-gov";

	@Autowired
	private RedisMessageListenerContainer container;

	@Autowired
	private UserRewardBctxService userRewardBctxService;

	@Autowired
	private AdminAlarmService adminAlarmService;

	@Autowired
	private MaMonitorService mamService;

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
				adminAlarmService.warn(logger,"UserReward Service SUSPENDED.");
				break;
			case "start service userreward":
				userRewardBctxService.resume();
				adminAlarmService.warn(logger,"UserReward Service RESUMED.");
				break;
			case "stop service mam":
				mamService.suspend();
				adminAlarmService.warn(logger,"MaMonitor Service SUSPENDED.");
				break;
			case "start service mam":
				mamService.resume();
				adminAlarmService.warn(logger,"MaMonitor Service RESUMED.");
				break;
			case "stop service all":
				DexGovStatus.isStopped = true;
				adminAlarmService.warn(logger,"Dex Governance Service SUSPENDED.");
				break;
			case "start service all":
				DexGovStatus.isStopped = false;
				adminAlarmService.warn(logger,"Dex Governance Service RESUMED.");
				break;
		}
	}
}
