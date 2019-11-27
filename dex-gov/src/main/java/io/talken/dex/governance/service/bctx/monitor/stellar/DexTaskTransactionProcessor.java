package io.talken.dex.governance.service.bctx.monitor.stellar;

import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.dex.shared.exception.TransactionResultProcessingException;
import io.talken.dex.shared.service.blockchain.stellar.StellarTxReceipt;

public interface DexTaskTransactionProcessor {
	DexTaskTypeEnum getDexTaskType();

	DexTaskTransactionProcessResult process(Long txmId, StellarTxReceipt taskTxResponse) throws TransactionResultProcessingException;
}