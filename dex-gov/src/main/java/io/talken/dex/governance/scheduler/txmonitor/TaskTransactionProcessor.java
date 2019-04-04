package io.talken.dex.governance.scheduler.txmonitor;

import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.dex.shared.exception.TransactionResultProcessingException;

public interface TaskTransactionProcessor {
	DexTaskTypeEnum getDexTaskType();

	TaskTransactionProcessResult process(Long txmId, TaskTransactionResponse taskTxResponse) throws TransactionResultProcessingException;
}
