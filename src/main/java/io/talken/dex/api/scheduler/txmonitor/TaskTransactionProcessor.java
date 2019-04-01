package io.talken.dex.api.scheduler.txmonitor;

import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.dex.api.exception.TransactionResultProcessingException;

public interface TaskTransactionProcessor {
	DexTaskTypeEnum getDexTaskType();

	TaskTransactionProcessResult process(TaskTransactionResponse taskTxResponse) throws TransactionResultProcessingException;
}
