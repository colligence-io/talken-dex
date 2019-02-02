package io.colligence.talken.dex.scheduler.dex;

import io.colligence.talken.dex.api.dex.DexTaskId;
import io.colligence.talken.dex.exception.TransactionResultProcessingException;
import org.stellar.sdk.responses.TransactionResponse;

public interface TaskTransactionProcessor {
	DexTaskId.Type getDexTaskType();

	TaskTransactionProcessResult process(DexTaskId dexTaskId, TransactionResponse txResponse) throws TransactionResultProcessingException;
}
