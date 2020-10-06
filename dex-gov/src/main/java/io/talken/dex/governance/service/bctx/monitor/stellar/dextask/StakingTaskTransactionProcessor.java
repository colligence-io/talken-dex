package io.talken.dex.governance.service.bctx.monitor.stellar.dextask;

import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskStakingRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.bctx.monitor.stellar.DexTaskTransactionProcessError;
import io.talken.dex.governance.service.bctx.monitor.stellar.DexTaskTransactionProcessResult;
import io.talken.dex.governance.service.bctx.monitor.stellar.DexTaskTransactionProcessor;
import io.talken.dex.shared.service.blockchain.stellar.StellarTxReceipt;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_STAKING;

@Component
public class StakingTaskTransactionProcessor implements DexTaskTransactionProcessor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(StakingTaskTransactionProcessor.class);

	@Autowired
	private DSLContext dslContext;

	@Override
	public DexTaskTypeEnum getDexTaskType() {
		return DexTaskTypeEnum.STAKING;
	}

	/**
	 * update dex_task_staking signTxCatchFlag
	 */
	@Override
	public DexTaskTransactionProcessResult process(Long txmId, StellarTxReceipt txResult) {
		try {
			Optional<DexTaskStakingRecord> opt_taskRecord = dslContext.selectFrom(DEX_TASK_STAKING)
                    .where(DEX_TASK_STAKING.TASKID.eq(txResult.getTaskId().getId())).fetchOptional();

			if(!opt_taskRecord.isPresent())
				throw new DexTaskTransactionProcessError("TaskIdNotFound");

            DexTaskStakingRecord taskRecord = opt_taskRecord.get();

			if(taskRecord.getSignedTxCatchFlag().equals(true))
				return DexTaskTransactionProcessResult.success();

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
