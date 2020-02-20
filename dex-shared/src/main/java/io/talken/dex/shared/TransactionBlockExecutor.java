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

	public TransactionBlockExecutor(DataSourceTransactionManager txMgr) {
		this.txMgr = txMgr;
	}

	@FunctionalInterface
	public static interface TransactionBlock {
		void runTransaction() throws Exception;
	}

	public TransactionStatus beginTransaction(TransactionDefinition definition) {
		return txMgr.getTransaction(definition);
	}

	public TransactionStatus beginTransaction() {
		return beginTransaction(new DefaultTransactionDefinition());
	}

	public void rollbackTransaction(TransactionStatus tx) {
		txMgr.rollback(tx);
	}

	public void commitTransaction(TransactionStatus tx) {
		txMgr.commit(tx);
	}

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

	public static TransactionBlockExecutor of(DataSourceTransactionManager txMgr) {
		return new TransactionBlockExecutor(txMgr);
	}
}
