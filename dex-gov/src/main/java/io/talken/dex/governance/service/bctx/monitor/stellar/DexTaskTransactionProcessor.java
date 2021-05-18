package io.talken.dex.governance.service.bctx.monitor.stellar;

import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.dex.shared.exception.TransactionResultProcessingException;
import io.talken.dex.shared.service.blockchain.stellar.StellarTxReceipt;

/**
 * The interface Dex task transaction processor.
 */
public interface DexTaskTransactionProcessor {
    /**
     * Gets dex task type.
     *
     * @return the dex task type
     */
    DexTaskTypeEnum getDexTaskType();

    /**
     * Process dex task transaction process result.
     *
     * @param txmId          the txm id
     * @param taskTxResponse the task tx response
     * @return the dex task transaction process result
     * @throws TransactionResultProcessingException the transaction result processing exception
     */
    DexTaskTransactionProcessResult process(Long txmId, StellarTxReceipt taskTxResponse) throws TransactionResultProcessingException;
}