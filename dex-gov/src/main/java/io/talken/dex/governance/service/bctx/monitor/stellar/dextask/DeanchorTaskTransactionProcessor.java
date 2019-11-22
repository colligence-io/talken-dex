package io.talken.dex.governance.service.bctx.monitor.stellar.dextask;

import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskDeanchorRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.bctx.monitor.stellar.DexTaskTransactionHandler;
import io.talken.dex.governance.service.bctx.monitor.stellar.DexTaskTransactionProcessError;
import io.talken.dex.governance.service.bctx.monitor.stellar.DexTaskTransactionProcessResult;
import io.talken.dex.shared.service.blockchain.stellar.StellarTxResult;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_DEANCHOR;

@Component
public class DeanchorTaskTransactionProcessor implements DexTaskTransactionHandler.TaskTransactionProcessor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(DeanchorTaskTransactionProcessor.class);

	@Autowired
	private DSLContext dslContext;

	@Override
	public DexTaskTypeEnum getDexTaskType() {
		return DexTaskTypeEnum.DEANCHOR;
	}

	@Override
	public DexTaskTransactionProcessResult process(Long txmId, StellarTxResult txResult) {
		try {
			Optional<DexTaskDeanchorRecord> opt_taskRecord = dslContext.selectFrom(DEX_TASK_DEANCHOR).where(DEX_TASK_DEANCHOR.TASKID.eq(txResult.getTaskId().getId())).fetchOptional();

			if(!opt_taskRecord.isPresent())
				throw new DexTaskTransactionProcessError("TaskIdNotFound");

			DexTaskDeanchorRecord taskRecord = opt_taskRecord.get();

			// update task as signed tx catched
			taskRecord.setSignedTxCatchFlag(true);
			taskRecord.update();
		} catch(DexTaskTransactionProcessError error) {
			return DexTaskTransactionProcessResult.error(error);
		} catch(Exception ex) {
			return DexTaskTransactionProcessResult.error("Processing", ex);
		}
		return DexTaskTransactionProcessResult.success();
	}
}
