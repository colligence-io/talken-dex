package io.talken.dex.governance.scheduler.txmonitor.processor;


import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.scheduler.txmonitor.TaskTransactionProcessResult;
import io.talken.dex.governance.scheduler.txmonitor.TaskTransactionProcessor;
import io.talken.dex.governance.scheduler.txmonitor.TaskTransactionResponse;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_REFUNDCREATEOFFERFEE;

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
	public TaskTransactionProcessResult process(Long txmId, TaskTransactionResponse taskTxResponse) {
		try {
			int updated = dslContext.update(DEX_TASK_REFUNDCREATEOFFERFEE)
					.set(DEX_TASK_REFUNDCREATEOFFERFEE.CHECKED_FLAG, true)
					.where(DEX_TASK_REFUNDCREATEOFFERFEE.TASKID.eq(taskTxResponse.getTaskId().getId()))
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


