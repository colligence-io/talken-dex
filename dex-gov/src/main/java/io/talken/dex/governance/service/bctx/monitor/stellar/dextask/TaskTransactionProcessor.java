package io.talken.dex.governance.service.bctx.monitor.stellar.dextask;

import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.dex.shared.exception.TransactionResultProcessingException;

public interface TaskTransactionProcessor {
	DexTaskTypeEnum getDexTaskType();

	TaskTransactionProcessResult process(Long txmId, TaskTransactionResponse taskTxResponse) throws TransactionResultProcessingException;
}
