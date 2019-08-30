package io.talken.dex.governance.scheduler.swap;

import io.talken.common.persistence.enums.DexSwapStatusEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskSwapLogRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskSwapRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.governance.service.integration.signer.SignServerService;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.stellar.sdk.KeyPair;

import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_SWAP;

public abstract class SwapTaskWorker {
	protected final PrefixedLogger logger;

	private Queue<Long> queue;

	@Autowired
	protected DSLContext dslContext;

	@Autowired
	protected AdminAlarmService adminAlarmService;

	@Autowired
	protected SignServerService signServerService;

	@Autowired
	protected StellarNetworkService stellarNetworkService;

	@Autowired
	protected TokenMetaGovService tmService;

	private KeyPair channel;

	private final String workerName;

	protected SwapTaskWorker() {
		this.logger = PrefixedLogger.getLogger(this.getClass());
		this.queue = new LinkedList<>();
		this.workerName = getClass().getSimpleName().replaceAll("^Swap", "").replaceAll("Worker$", "");
	}

	public final String getName() {
		return this.workerName;
	}

	protected KeyPair getChannel() {
		if(this.channel != null) return channel;
		throw new IllegalArgumentException("Stellar Channel for " + this.workerName + " is not configured.");
	}

	public void setChannel(KeyPair channel) {
		this.channel = channel;
	}

	public abstract DexSwapStatusEnum getStartStatus();

	protected abstract int getRetryCount();

	protected abstract Duration getRetryInterval();

	public boolean queue(DexTaskSwapRecord record) {
		if(!queue.contains(record.getId())) {
			if(queue.offer(record.getId())) {
				logger.info("Swap task {}(#{}) = {} : queued (qs = {})", record.getTaskid(), record.getId(), record.getStatus(), queue.size());
				return true;
			} else {
				return false;
			}
		} else {
			return true;
		}
	}

	@Scheduled(fixedDelay = 10)
	private void checkQueueAndProceed() {
		while(!Thread.interrupted() && queue.size() > 0) {
			// peek queue
			Long currentTask = queue.peek();

			DexTaskSwapRecord record = dslContext.selectFrom(DEX_TASK_SWAP).where(DEX_TASK_SWAP.ID.eq(currentTask)).fetchOne();
			if(record != null) {
				if(record.getStatus().equals(getStartStatus())) {
					DexSwapStatusEnum statusFrom = record.getStatus();
					logger.trace("Swap task {}(#{}) = {} : started", record.getTaskid(), record.getId(), statusFrom);
					try {
						WorkerProcessResult pResult = proceed(record);
						if(pResult.isSuccess()) {
							logger.debug("Swap task {}(#{}) = {} -> {} : success", record.getTaskid(), record.getId(), statusFrom, record.getStatus());
						} else {
							addProcessLog(pResult);
							if(record.getScheduleTimestamp() != null) {
								logger.error("Swap task {}(#{}) = {} -> {} : failover at {}", record.getTaskid(), record.getId(), statusFrom, record.getStatus(), record.getScheduleTimestamp());
							} else {
								logger.error("Swap task {}(#{}) = {} -> {} : fail", record.getTaskid(), record.getId(), statusFrom, record.getStatus());
							}
						}
					} catch(Exception ex) {
						// HALT Task
						record.setStatus(DexSwapStatusEnum.TASK_HALTED);
						record.setFinishFlag(true);
						record.update();
						addProcessLog(new WorkerProcessResult.Builder(this, record).exception("Unhandled", ex));
						adminAlarmService.exception(logger, ex, "Swap task {}(#{}) = {} : unhandled exception catched", record.getTaskid(), record.getId(), record.getStatus());
					} finally {
						// dequeue current
						queue.poll();
					}
				} else { // record.status != getStartStatus
					adminAlarmService.error(logger, "Swap task {}(#{}) is in {} status, expected {}", record.getTaskid(), record.getId(), record.getStatus(), getStartStatus());
				}
			} else { // record == null
				adminAlarmService.error(logger, "Swap task #{} not found");
			}
		}
	}

	private void addProcessLog(WorkerProcessResult result) {
		if(!result.isSuccess()) {
			DexTaskSwapLogRecord logRecord = result.newLogRecord();
			dslContext.attach(logRecord);
			logRecord.store();

			if(result.getTaskRecord().getStatus().isAlarm()) {
				adminAlarmService.error(logger, "Swap task {}(#{}) = {} : [{}] {} - {}", result.getTaskRecord().getTaskid(), result.getTaskRecord().getId(), result.getTaskRecord().getStatus(), result.getErrorPosition(), result.getErrorCode(), result.getErrorMessage());
			}
		}
	}

	public abstract WorkerProcessResult proceed(DexTaskSwapRecord record) throws Exception;
}
