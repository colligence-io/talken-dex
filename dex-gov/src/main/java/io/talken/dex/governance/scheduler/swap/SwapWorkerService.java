package io.talken.dex.governance.scheduler.swap;

import io.talken.common.persistence.enums.DexSwapStatusEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskSwapRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.UTCUtil;
import io.talken.common.util.integration.slack.AdminAlarmService;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_SWAP;

@Service
@Scope("singleton")
@RequiredArgsConstructor
public class SwapWorkerService implements ApplicationContextAware {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(SwapWorkerService.class);

	// constructor injection
	private final DSLContext dslContext;
	private final AdminAlarmService adminAlarmService;

	private ApplicationContext applicationContext;

	private Map<DexSwapStatusEnum, SwapTaskWorker> workers = new HashMap<>();


	@Override
	public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@PostConstruct
	private void init() {
		Map<String, SwapTaskWorker> ascBeans = applicationContext.getBeansOfType(SwapTaskWorker.class);
		for(Map.Entry<String, SwapTaskWorker> entry : ascBeans.entrySet()) {
			SwapTaskWorker _asc = entry.getValue();
			workers.put(_asc.getStartStatus(), _asc);
			logger.info("SwapTaskWorker for [{}] started.", _asc.getStartStatus());
		}
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
