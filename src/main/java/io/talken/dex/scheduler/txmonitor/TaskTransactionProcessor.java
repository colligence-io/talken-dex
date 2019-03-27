package io.talken.dex.scheduler.txmonitor;

import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.dex.exception.TransactionResultProcessingException;

public interface TaskTransactionProcessor {
	DexTaskTypeEnum getDexTaskType();

	TaskTransactionProcessResult process(TaskTransactionResponse taskTxResponse) throws TransactionResultProcessingException;
}
