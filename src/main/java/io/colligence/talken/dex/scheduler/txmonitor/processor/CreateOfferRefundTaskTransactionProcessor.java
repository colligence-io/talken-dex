package io.colligence.talken.dex.scheduler.txmonitor.processor;


import io.colligence.talken.common.persistence.enums.DexTaskTypeEnum;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.scheduler.txmonitor.TaskTransactionProcessResult;
import io.colligence.talken.dex.scheduler.txmonitor.TaskTransactionProcessor;
import io.colligence.talken.dex.scheduler.txmonitor.TaskTransactionResponse;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static io.colligence.talken.common.persistence.jooq.Tables.DEX_CREATEOFFER_REFUND_TASK;

@Component
public class CreateOfferRefundTaskTransactionProcessor implements TaskTransactionProcessor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(CreateOfferRefundTaskTransactionProcessor.class);

	@Autowired
	private DSLContext dslContext;

	@Override
	public DexTaskTypeEnum getDexTaskType() {
		return DexTaskTypeEnum.OFFER_REFUNDFEE;
	}

	@Override
	public TaskTransactionProcessResult process(TaskTransactionResponse taskTxResponse) {
		try {
			int updated = dslContext.update(DEX_CREATEOFFER_REFUND_TASK)
					.set(DEX_CREATEOFFER_REFUND_TASK.CHECKED_FLAG, true)
					.where(DEX_CREATEOFFER_REFUND_TASK.TASKID.eq(taskTxResponse.getTaskId().getId()))
					.execute();

			if(updated == 0)
				logger.error("{} not updated", taskTxResponse.getTaskId());
			else
				logger.info("{} marked as checked.", taskTxResponse.getTaskId());
		} catch(Exception ex) {
			return TaskTransactionProcessResult.error("Processing", ex);
		}
		return TaskTransactionProcessResult.success();
	}
}


