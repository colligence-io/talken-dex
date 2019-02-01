package io.colligence.talken.dex.scheduler;

import io.colligence.talken.dex.api.dex.DexTaskId;
import io.colligence.talken.dex.exception.TransactionResultProcessingException;
import org.stellar.sdk.responses.TransactionResponse;

public interface TaskTransactionProcessor {
	DexTaskId.Type getDexTaskType();

	void process(DexTaskId dexTaskId, TransactionResponse tx) throws TransactionResultProcessingException;
}
