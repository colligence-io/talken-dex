package io.talken.dex.governance.scheduler.txmonitor.processor;


import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskRefundcreateofferfeeRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.scheduler.txmonitor.TaskTransactionProcessResult;
import io.talken.dex.governance.scheduler.txmonitor.TaskTransactionProcessor;
import io.talken.dex.governance.scheduler.txmonitor.TaskTransactionResponse;
import io.talken.dex.shared.TransactionBlockExecutor;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_REFUNDCREATEOFFERFEE;
import static io.talken.common.persistence.jooq.Tables.DEX_TQUEUE_REFUNDCREATEOFFERFEE;

@Component
public class CreateOfferRefundTaskTransactionProcessor implements TaskTransactionProcessor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(CreateOfferRefundTaskTransactionProcessor.class);

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private DataSourceTransactionManager txMgr;

	@Override
	public DexTaskTypeEnum getDexTaskType() {
		return DexTaskTypeEnum.OFFER_REFUNDFEE;
	}

	@Override
	public TaskTransactionProcessResult process(Long txmId, TaskTransactionResponse taskTxResponse) {
		try {
			TransactionBlockExecutor.of(txMgr).transactional(() -> {

				Optional<DexTaskRefundcreateofferfeeRecord> opt_taskRecord = dslContext.selectFrom(DEX_TASK_REFUNDCREATEOFFERFEE)
						.where(DEX_TASK_REFUNDCREATEOFFERFEE.TASKID.eq(taskTxResponse.getTaskId().getId()).and(DEX_TASK_REFUNDCREATEOFFERFEE.TX_HASH.eq(taskTxResponse.getTxHash())))
						.fetchOptional();

				if(!opt_taskRecord.isPresent()) {
					logger.error("{} with txHash {} not found, this can be caused by unexpected refund tx.");
				} else {
					DexTaskRefundcreateofferfeeRecord taskRecord = opt_taskRecord.get();
					taskRecord.setSignedTxCatchFlag(true);
					taskRecord.update();
				}

				int tqUpdated = dslContext.update(DEX_TQUEUE_REFUNDCREATEOFFERFEE)
						.set(DEX_TQUEUE_REFUNDCREATEOFFERFEE.FINISHED_FLAG, true)
						.where(DEX_TQUEUE_REFUNDCREATEOFFERFEE.TASKID.eq(taskTxResponse.getTaskId().getId()))
						.execute();

				if(tqUpdated == 0)
					logger.error("TaskQueue for {} not updated (not found)", taskTxResponse.getTaskId());
				else
					logger.info("TaskQueue for {} marked as finished.", taskTxResponse.getTaskId());
			});
		} catch(Exception ex) {
			return TaskTransactionProcessResult.error("Processing", ex);
		}
		return TaskTransactionProcessResult.success();
	}
}


