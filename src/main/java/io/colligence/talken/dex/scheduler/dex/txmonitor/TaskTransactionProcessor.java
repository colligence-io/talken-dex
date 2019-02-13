package io.colligence.talken.dex.scheduler.dex.txmonitor;

import io.colligence.talken.common.persistence.enums.DexTaskTypeEnum;
import io.colligence.talken.dex.api.dex.DexTaskId;
import io.colligence.talken.dex.exception.TransactionResultProcessingException;
import org.stellar.sdk.responses.TransactionResponse;

public interface TaskTransactionProcessor {
	DexTaskTypeEnum getDexTaskType();

	TaskTransactionProcessResult process(TaskTransactionResponse taskTxResponse) throws TransactionResultProcessingException;
}
