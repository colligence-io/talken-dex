package io.talken.dex.governance.scheduler.txmonitor.processor;

import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskDeleteofferRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.scheduler.txmonitor.TaskTransactionProcessError;
import io.talken.dex.governance.scheduler.txmonitor.TaskTransactionProcessResult;
import io.talken.dex.governance.scheduler.txmonitor.TaskTransactionProcessor;
import io.talken.dex.governance.scheduler.txmonitor.TaskTransactionResponse;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_DELETEOFFER;

@Component
public class DeleteOfferTaskTransactionProcessor implements TaskTransactionProcessor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(DeleteOfferTaskTransactionProcessor.class);

	@Autowired
	private DSLContext dslContext;

	@Override
	public DexTaskTypeEnum getDexTaskType() {
		return DexTaskTypeEnum.OFFER_DELETE;
	}

	@Override
	public TaskTransactionProcessResult process(Long txmId, TaskTransactionResponse taskTxResponse) {
		try {
			Optional<DexTaskDeleteofferRecord> opt_taskRecord = dslContext.selectFrom(DEX_TASK_DELETEOFFER).where(DEX_TASK_DELETEOFFER.TASKID.eq(taskTxResponse.getTaskId().getId())).fetchOptional();

			if(!opt_taskRecord.isPresent())
				throw new TaskTransactionProcessError("TaskIdNotFound");

			DexTaskDeleteofferRecord taskRecord = opt_taskRecord.get();

			// update task as signed tx catched
			taskRecord.setSignedTxCatchFlag(true);
			taskRecord.update();
		} catch(TaskTransactionProcessError error) {
			return TaskTransactionProcessResult.error(error);
		} catch(Exception ex) {
			return TaskTransactionProcessResult.error("Processing", ex);
		}
		return TaskTransactionProcessResult.success();
	}
}