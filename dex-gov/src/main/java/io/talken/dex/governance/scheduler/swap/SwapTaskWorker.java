package io.talken.dex.governance.scheduler.swap;

import io.talken.common.persistence.enums.DexSwapStatusEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskSwapRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.common.util.integration.slack.AdminAlarmService;
import io.talken.dex.governance.service.TokenMetaGovService;
import io.talken.dex.governance.service.integration.signer.SignServerService;
import io.talken.dex.shared.service.blockchain.stellar.StellarNetworkService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.stellar.sdk.KeyPair;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_SWAP;

public abstract class SwapTaskWorker implements Runnable {
	protected final PrefixedLogger logger;

	private BlockingQueue<Long> queue;

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

	private Long currentTask = null;

	protected SwapTaskWorker() {
		this.logger = PrefixedLogger.getLogger(this.getClass());
		this.queue = new LinkedBlockingQueue<>();
	}

	protected KeyPair getChannel() {
		return channel;
	}

	public void setChannel(KeyPair channel) {
		this.channel = channel;
	}

	public abstract DexSwapStatusEnum getStartStatus();

	public boolean queue(DexTaskSwapRecord record) {
		// BlockingQueue.offer will return false if enqueue failed
		if(!queue.contains(record.getId())) {
			if(currentTask != null && currentTask.equals(record.getId())) return true;
			if(queue.offer(record.getId())) {
				logger.debug("Swap task {}(#{}) = {} : queued", record.getTaskid(), record.getId(), record.getStatus());
				return true;
			} else {
				return false;
			}
		} else {
			return true;
		}
	}

	@Override
	public void run() {
		while(!Thread.interrupted()) {
			try {
				// BlockingQueue.take will wait until queued element is available
				currentTask = queue.take();

				DexTaskSwapRecord record = dslContext.selectFrom(DEX_TASK_SWAP).where(DEX_TASK_SWAP.ID.eq(currentTask)).fetchOne();
				if(record != null) {
					if(record.getStatus().equals(getStartStatus())) {
						logger.debug("Swap task {}(#{}) = {} : process started", record.getTaskid(), record.getId(), record.getStatus());
						try {
							proceed(record);
							if(record.getSuccessFlag() != null && !record.getSuccessFlag()) {
								adminAlarmService.error(logger, "Swap task {}(#{}) = {} : process failed", record.getTaskid(), record.getId(), record.getStatus());
							}
							else {
								logger.debug("Swap task {}(#{}) = {} : process finished successfully", record.getTaskid(), record.getId(), record.getStatus());
							}
						} catch(Exception ex) {
							adminAlarmService.exception(logger, ex, "Swap task {}(#{}) = {} : unhandled exception catched", record.getTaskid(), record.getId(), record.getStatus());
						} finally {
							// clear current status
							currentTask = null;
						}
					} else { // record.status != getStartStatus
						adminAlarmService.error(logger, "Swap task {}(#{}) is in {} status, expected {}", record.getTaskid(), record.getId(), record.getStatus(), getStartStatus());
					}
				} else { // record == null
					adminAlarmService.error(logger, "Swap task #{} not found");
				}

			} catch(InterruptedException ex) {
				logger.warn("{} interrupted.", this.getClass().getSimpleName());
			}

		}
	}

	protected void updateRecordException(DexTaskSwapRecord record, DexSwapStatusEnum status, String position, Exception ex) {
		final String pos = this.getClass().getSimpleName() + "(" + position + ")";
		adminAlarmService.exception(logger, ex, "{} occured at {} : {}", ex.getClass().getSimpleName(), pos, ex.getMessage());
		record.setFinishFlag(true);
		record.setSuccessFlag(false);
		record.setStatus(status);
		record.setErrorposition(pos);
		record.setErrorcode(ex.getClass().getSimpleName());
		record.setErrormessage(ex.getMessage());
		record.update();
	}

	protected void updateRecordError(DexTaskSwapRecord record, DexSwapStatusEnum status, String position, String errorCode, String errorMessage) {
		final String pos = this.getClass().getSimpleName() + "(" + position + ")";
		adminAlarmService.error(logger, "{} failed - {} : {}", pos, errorCode, errorMessage);
		record.setFinishFlag(true);
		record.setSuccessFlag(false);
		record.setStatus(status);
		record.setErrorposition(pos);
		record.setErrorcode(errorCode);
		record.setErrormessage(errorMessage);
		record.update();
	}

	public abstract void proceed(DexTaskSwapRecord record) throws Exception;
}
