package io.talken.dex.governance.service.bctx.monitor.stellar;

import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTxmonRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.service.bctx.TxMonitor;
import io.talken.dex.shared.exception.TransactionResultProcessingException;
import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarTxResult;
import org.jooq.DSLContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;


@Component
@Scope("singleton")
public class DexTaskTransactionHandler implements ApplicationContextAware, TxMonitor.TransactionHandler<StellarTxResult> {
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
	public void handle(StellarTxResult txResult) throws Exception {
		if(txResult.getTaskId() == null) return;

		DexTxmonRecord txmRecord = new DexTxmonRecord();
		txmRecord.setTxid(txResult.getResponse().getHash());
		txmRecord.setMemotaskid(txResult.getTaskId().getId());
		txmRecord.setTasktype(txResult.getTaskId().getType());
		txmRecord.setTxhash(txResult.getResponse().getHash());
		txmRecord.setLedger(txResult.getResponse().getLedger());
		txmRecord.setCreatedat(StellarConverter.toLocalDateTime(txResult.getResponse().getCreatedAt()));
		txmRecord.setSourceaccount(txResult.getResponse().getSourceAccount());
		txmRecord.setEnvelopexdr(txResult.getResponse().getEnvelopeXdr());
		txmRecord.setResultxdr(txResult.getResponse().getResultXdr());
		txmRecord.setResultmetaxdr(txResult.getResponse().getResultMetaXdr());
		txmRecord.setFeepaid(txResult.getResponse().getFeePaid());
		dslContext.attach(txmRecord);
		txmRecord.store();

		try {
			// FIXME : check before applying stellar-sdk 0.9.0
			txmRecord.setOfferidfromresult(txResult.getOfferIdFromResult());

			// run processor
			if(processors.containsKey(txResult.getTaskId().getType())) {
				DexTaskTransactionProcessResult result;
				try {
					logger.info("{} ({}) found. start processing.", txResult.getTaskId(), txResult.getTxHash());
					result = processors.get(txResult.getTaskId().getType()).process(txmRecord.getId(), txResult);
				} catch(Exception ex) {
					result = DexTaskTransactionProcessResult.error("Unknown", ex);
				}

				if(result.isSuccess()) {
					txmRecord.setProcessSuccessFlag(true);
				} else {
					logger.error("{} transaction {} result process error : {} {}", txResult.getTaskId(), txResult.getTxHash(), result.getError().getCode(), result.getError().getMessage());

					// log exception
					if(result.getError().getCause() != null)
						logger.exception(result.getError().getCause());

					txmRecord.setProcessSuccessFlag(false);
					txmRecord.setErrorcode(result.getError().getCode());
					txmRecord.setErrormessage(result.getError().getMessage());
				}

				txmRecord.update();
			} else {
				logger.verbose("No TaskTransactionProcessor for {} registered", txResult.getTaskId().getType());
			}
		} catch(Exception ex) {
			logger.exception(ex);
			txmRecord.setProcessSuccessFlag(false);
			txmRecord.setErrorcode(ex.getClass().getSimpleName());
			txmRecord.setErrormessage(ex.getMessage());
		}

		txmRecord.update();
	}

	public static interface TaskTransactionProcessor {
		DexTaskTypeEnum getDexTaskType();

		DexTaskTransactionProcessResult process(Long txmId, StellarTxResult taskTxResponse) throws TransactionResultProcessingException;
	}
}
