package io.colligence.talken.dex.scheduler.txmonitor;

import io.colligence.talken.common.persistence.enums.DexTaskTypeEnum;
import io.colligence.talken.dex.exception.TransactionResultProcessingException;

public interface TaskTransactionProcessor {
	DexTaskTypeEnum getDexTaskType();

	TaskTransactionProcessResult process(TaskTransactionResponse taskTxResponse) throws TransactionResultProcessingException;
}
