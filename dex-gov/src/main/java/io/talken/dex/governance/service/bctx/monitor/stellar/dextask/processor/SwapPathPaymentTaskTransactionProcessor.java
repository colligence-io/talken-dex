package io.talken.dex.governance.service.bctx.monitor.stellar.dextask.processor;

import io.talken.common.persistence.enums.DexSwapStatusEnum;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskDeleteofferRecord;
import io.talken.common.persistence.jooq.tables.records.DexTaskSwapRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.bctx.monitor.stellar.dextask.TaskTransactionProcessError;
import io.talken.dex.governance.service.bctx.monitor.stellar.dextask.TaskTransactionProcessResult;
import io.talken.dex.governance.service.bctx.monitor.stellar.dextask.TaskTransactionProcessor;
import io.talken.dex.governance.service.bctx.monitor.stellar.dextask.TaskTransactionResponse;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_DELETEOFFER;
import static io.talken.common.persistence.jooq.Tables.DEX_TASK_SWAP;

@Component
public class SwapPathPaymentTaskTransactionProcessor implements TaskTransactionProcessor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(SwapPathPaymentTaskTransactionProcessor.class);

	@Autowired
	private DSLContext dslContext;

	@Override
	public DexTaskTypeEnum getDexTaskType() {
		return DexTaskTypeEnum.SWAP_PATHPAYMENT;
	}

	@Override
	public TaskTransactionProcessResult process(Long txmId, TaskTransactionResponse taskTxResponse) {
		try {
			Optional<DexTaskSwapRecord> opt_taskRecord = dslContext.selectFrom(DEX_TASK_SWAP).where(DEX_TASK_SWAP.TASKID.eq(taskTxResponse.getTaskId().getId())).fetchOptional();

			if(!opt_taskRecord.isPresent())
				throw new TaskTransactionProcessError("TaskIdNotFound");

			DexTaskSwapRecord taskRecord = opt_taskRecord.get();

			// update task as signed tx catched
			taskRecord.setStatus(DexSwapStatusEnum.PATHPAYMENT_TX_CATCH);
			taskRecord.update();
		} catch(TaskTransactionProcessError error) {
			return TaskTransactionProcessResult.error(error);
		} catch(Exception ex) {
			return TaskTransactionProcessResult.error("Processing", ex);
		}
		return TaskTransactionProcessResult.success();
	}
}
