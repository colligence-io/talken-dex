package io.talken.dex.shared;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * Database Transaction Block Executor (implicit way)
 */
public class TransactionBlockExecutor {

	private DataSourceTransactionManager txMgr;

    /**
     * Instantiates a new Transaction block executor.
     *
     * @param txMgr the tx mgr
     */
    public TransactionBlockExecutor(DataSourceTransactionManager txMgr) {
		this.txMgr = txMgr;
	}

    /**
     * The interface Transaction block.
     */
    @FunctionalInterface
	public static interface TransactionBlock {
        /**
         * Run transaction.
         *
         * @throws Exception the exception
         */
        void runTransaction() throws Exception;
	}

    /**
     * Begin transaction transaction status.
     *
     * @param definition the definition
     * @return the transaction status
     */
    public TransactionStatus beginTransaction(TransactionDefinition definition) {
		return txMgr.getTransaction(definition);
	}

    /**
     * Begin transaction transaction status.
     *
     * @return the transaction status
     */
    public TransactionStatus beginTransaction() {
		return beginTransaction(new DefaultTransactionDefinition());
	}

    /**
     * Rollback transaction.
     *
     * @param tx the tx
     */
    public void rollbackTransaction(TransactionStatus tx) {
		txMgr.rollback(tx);
	}

    /**
     * Commit transaction.
     *
     * @param tx the tx
     */
    public void commitTransaction(TransactionStatus tx) {
		txMgr.commit(tx);
	}

    /**
     * Transactional.
     *
     * @param block the block
     * @throws Exception the exception
     */
    public void transactional(TransactionBlock block) throws Exception {
		TransactionStatus tx = beginTransaction();
		try {
			block.runTransaction();
			commitTransaction(tx);
		} catch(Exception ex) {
			rollbackTransaction(tx);
			throw ex;
		}
	}

    /**
     * Of transaction block executor.
     *
     * @param txMgr the tx mgr
     * @return the transaction block executor
     */
    public static TransactionBlockExecutor of(DataSourceTransactionManager txMgr) {
		return new TransactionBlockExecutor(txMgr);
	}
}
