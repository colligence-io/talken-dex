package io.talken.dex.governance.service.bctx.monitor.stellar.dextask;

import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTxmonRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.bctx.TxMonitor;
import io.talken.dex.governance.service.bctx.monitor.stellar.StellarTxMonitor;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.exception.TaskIntegrityCheckFailedException;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import org.jooq.DSLContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.stellar.sdk.Memo;
import org.stellar.sdk.MemoText;
import org.stellar.sdk.responses.TransactionResponse;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;


@Component
@Scope("singleton")
public class DexTaskTransactionHandler implements ApplicationContextAware, TxMonitor.TransactionHandler<TransactionResponse> {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(DexTaskTransactionHandler.class);

	@Autowired
	private StellarTxMonitor txMonitor;

	@Autowired
	private DSLContext dslContext;

	private HashMap<DexTaskTypeEnum, TaskTransactionProcessor> processors = new HashMap<>();

	private ApplicationContext applicationContext;

	private static final int TXREQUEST_LIMIT = 200;

	@Override
	public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@PostConstruct
	private void init() {
		txMonitor.addTransactionHandler(this);

		Map<String, TaskTransactionProcessor> ascBeans = applicationContext.getBeansOfType(TaskTransactionProcessor.class);
		ascBeans.forEach((_name, _asc) -> {
			processors.put(_asc.getDexTaskType(), _asc);
			logger.info("DexTaskTransactionProcessor for [{}] registered.", _asc.getDexTaskType());
		});
	}

	@Override
	public boolean handle(TransactionResponse tx) throws Exception {
		try {
			Memo memo = tx.getMemo();
			if(memo instanceof MemoText) {
				String memoText = ((MemoText) memo).getText();
				if(memoText.startsWith("TALKEN")) {
					DexTxmonRecord txmRecord = new DexTxmonRecord();
					txmRecord.setTxid(tx.getHash());
					txmRecord.setTxhash(tx.getHash());
					txmRecord.setLedger(tx.getLedger());
					txmRecord.setCreatedat(StellarConverter.toLocalDateTime(tx.getCreatedAt()));
					txmRecord.setSourceaccount(tx.getSourceAccount());
					txmRecord.setEnvelopexdr(tx.getEnvelopeXdr());
					txmRecord.setResultxdr(tx.getResultXdr());
					txmRecord.setResultmetaxdr(tx.getResultMetaXdr());
					txmRecord.setFeepaid(tx.getFeePaid());
					dslContext.attach(txmRecord);
					txmRecord.store();

					try {
						DexTaskId dexTaskId = DexTaskId.decode_taskId(memoText);

						txmRecord.setMemotaskid(dexTaskId.getId());
						txmRecord.setTasktype(dexTaskId.getType());


						// FIXME : check before applying stellar-sdk 0.9.0
						TaskTransactionResponse txResponse = new TaskTransactionResponse(dexTaskId, tx);
						txmRecord.setOfferidfromresult(txResponse.getOfferIdFromResult());

						// run processor
						if(processors.containsKey(dexTaskId.getType())) {

							TaskTransactionProcessResult result;
							try {
								logger.info("{} ({}) found. start processing.", dexTaskId, txResponse.getTxHash());
								result = processors.get(dexTaskId.getType()).process(txmRecord.getId(), txResponse);
							} catch(Exception ex) {
								result = TaskTransactionProcessResult.error("Unknown", ex);
							}

							if(result.isSuccess()) {
								txmRecord.setProcessSuccessFlag(true);
							} else {
								logger.error("{} transaction {} result process error : {} {}", dexTaskId, tx.getHash(), result.getError().getCode(), result.getError().getMessage());

								// log exception
								if(result.getError().getCause() != null)
									logger.exception(result.getError().getCause());

								txmRecord.setProcessSuccessFlag(false);
								txmRecord.setErrorcode(result.getError().getCode());
								txmRecord.setErrormessage(result.getError().getMessage());
							}
						} else {
							logger.debug("No txSender for {} registered", dexTaskId);
						}
					} catch(TaskIntegrityCheckFailedException e) {
						logger.error("Invalid DexTaskId [{}] detected : txHash = {}", memoText, tx.getHash());
						txmRecord.setProcessSuccessFlag(false);
						txmRecord.setErrorcode("InvalidTaskID");
						txmRecord.setErrormessage(memoText + " is a invalid taskid. integrity check failed.");
					} catch(Exception ex) {
						logger.exception(ex);
						txmRecord.setProcessSuccessFlag(false);
						txmRecord.setErrorcode(ex.getClass().getSimpleName());
						txmRecord.setErrormessage(ex.getMessage());
					}

					txmRecord.update();
				}
			}
			return true;
		} catch(Exception ex) {
			logger.exception(ex, "Unidentified exception occured.");
			return false;
		}
	}
}