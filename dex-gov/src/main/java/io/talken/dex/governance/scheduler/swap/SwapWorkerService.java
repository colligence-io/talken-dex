package io.talken.dex.governance.scheduler.swap;

import io.talken.common.persistence.enums.DexSwapStatusEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskSwapRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.GovSettings;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.TaskExecutor;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.responses.AccountResponse;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_SWAP;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class SwapWorkerService implements ApplicationContextAware {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(SwapWorkerService.class);

	// constructor injection
	private final StellarNetworkService stellarNetworkService;
	private final GovSettings govSettings;
	private final DSLContext dslContext;
	private final AdminAlarmService adminAlarmService;

	private ApplicationContext applicationContext;

	private Map<DexSwapStatusEnum, SwapTaskWorker> workers = new HashMap<>();


	@Override
	public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@PostConstruct
	private void init() throws IOException {
		Map<String, SwapTaskWorker> ascBeans = applicationContext.getBeansOfType(SwapTaskWorker.class);
		for(Map.Entry<String, SwapTaskWorker> entry : ascBeans.entrySet()) {
			SwapTaskWorker _asc = entry.getValue();
			String workerName = _asc.getName();
			_asc.setChannel(getChannelKeyPair(workerName));
			workers.put(_asc.getStartStatus(), _asc);
			logger.info("SwapTaskWorker for [{}] started.", _asc.getStartStatus());
		}
	}

	private KeyPair getChannelKeyPair(String workerName) throws IOException {
		GovSettings._Task._Swap._Channel channel = govSettings.getTask().getSwap().getWorkerChannel().get(workerName);
		if(channel == null) return null;
		KeyPair chkp = KeyPair.fromSecretSeed(channel.getSecretKey());
		if(!chkp.getAccountId().equals(channel.getPublicKey()))
			throw new IllegalArgumentException("Stellar Channel for " + workerName + " is not match with given secretKey.");

		AccountResponse account = stellarNetworkService.pickServer().accounts().account(chkp.getAccountId());
		return chkp;
	}

	@Scheduled(fixedDelay = 1000, initialDelay = 5000)
	private void checkTask() {
		Result<DexTaskSwapRecord> tasks = dslContext
				.selectFrom(DEX_TASK_SWAP)
				.where(
						DEX_TASK_SWAP.FINISH_FLAG.eq(false)
								.and(DEX_TASK_SWAP.SCHEDULE_TIMESTAMP.isNull().or(DEX_TASK_SWAP.SCHEDULE_TIMESTAMP.lt(UTCUtil.getNow())))
				)
				.fetch();

		for(DexTaskSwapRecord task : tasks) {
			SwapTaskWorker worker = workers.get(task.getStatus());
			if(worker != null) {
				if(!worker.queue(task)) {
					adminAlarmService.error(logger, "Cannot queue swap task {} for {} status", task.getTaskid(), task.getStatus());
				}
			}
		}
	}
}
