package io.talken.dex.scheduler.txmonitor;

import io.talken.common.RunningProfile;
import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexStatusRecord;
import io.talken.common.persistence.jooq.tables.records.DexTxResultRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.api.service.DexTaskId;
import io.talken.dex.api.service.DexTaskIdService;
import io.talken.dex.exception.TaskIntegrityCheckFailedException;
import io.talken.dex.service.integration.stellar.StellarNetworkService;
import io.talken.dex.util.StellarConverter;
import org.jooq.DSLContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.stellar.sdk.Memo;
import org.stellar.sdk.MemoText;
import org.stellar.sdk.Server;
import org.stellar.sdk.requests.RequestBuilder;
import org.stellar.sdk.responses.Page;
import org.stellar.sdk.responses.TransactionResponse;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.DEX_STATUS;

@Service
@Scope("singleton")
public class TaskTransactionMonitor implements ApplicationContextAware {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(TaskTransactionMonitor.class);

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private DexTaskIdService taskIdService;

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
		// reset status if local
		if(RunningProfile.isLocal()) {
			dslContext.deleteFrom(DEX_STATUS).where().execute();
		}
		Map<String, TaskTransactionProcessor> ascBeans = applicationContext.getBeansOfType(TaskTransactionProcessor.class);
		ascBeans.forEach((_name, _asc) -> {
			processors.put(_asc.getDexTaskType(), _asc);
			logger.debug("DexTaskTransactionProcessor for [{}] registered.", _asc.getDexTaskType());
		});
	}

	@Scheduled(fixedDelay = 4000, initialDelay = 10000)
	private void checkTask() {
		int processed = -1;
		do {
			processed = processNextTransactions();
		} while(processed == TXREQUEST_LIMIT);
	}

	private int processNextTransactions() {
		Optional<DexStatusRecord> opt_status = dslContext.selectFrom(DEX_STATUS).limit(1).fetchOptional();

		Server server = stellarNetworkService.pickServer();

		Page<TransactionResponse> txPage;
		try {
			if(opt_status.isPresent()) {
				// 200 is maximum
				txPage = server.transactions().order(RequestBuilder.Order.ASC).cursor(opt_status.get().getTxmonitorlastpagingtoken()).limit(TXREQUEST_LIMIT).includeFailed(false).execute();
			} else {
				// insert initial row
				dslContext.insertInto(DEX_STATUS).columns(DEX_STATUS.TXMONITORLASTPAGINGTOKEN).values("0").execute();
				// get last tx for initiation
				txPage = server.transactions().order(RequestBuilder.Order.DESC).limit(1).includeFailed(false).execute();
			}
		} catch(Exception ex) {
			logger.exception(ex, "Cannot get last tx from stellar network.");
			return -1;
		}

		return processTransactionPage(txPage);
	}

	private int processTransactionPage(Page<TransactionResponse> txPage) {
		int processed = 0;

		for(TransactionResponse txRecord : txPage.getRecords()) {
			try {
				Memo memo = txRecord.getMemo();
				if(memo instanceof MemoText) {
					String memoText = ((MemoText) memo).getText();
					if(memoText.startsWith("TALKEN")) {
						DexTxResultRecord resultRecord = new DexTxResultRecord();
						resultRecord.setTxid(txRecord.getHash());
						resultRecord.setTxhash(txRecord.getHash());
						resultRecord.setLedger(txRecord.getLedger());
						resultRecord.setCreatedat(StellarConverter.toLocalDateTime(txRecord.getCreatedAt()));
						resultRecord.setSourceaccount(txRecord.getSourceAccount().getAccountId());
						resultRecord.setEnvelopexdr(txRecord.getEnvelopeXdr());
						resultRecord.setResultxdr(txRecord.getResultXdr());
						resultRecord.setResultmetaxdr(txRecord.getResultMetaXdr());
						resultRecord.setFeepaid(txRecord.getFeePaid());

						try {
							DexTaskId dexTaskId = taskIdService.decode_taskId(memoText);

							resultRecord.setMemotaskid(dexTaskId.getId());
							resultRecord.setTasktype(dexTaskId.getType());

							TaskTransactionResponse txResponse = new TaskTransactionResponse(dexTaskId, txRecord);
							resultRecord.setOfferidfromresult(txResponse.getOfferIdFromResult());

							// run processor
							if(processors.containsKey(dexTaskId.getType())) {

								TaskTransactionProcessResult result;
								try {
									logger.info("{} ({}) found. start processing.", dexTaskId, txResponse.getTxHash());
									result = processors.get(dexTaskId.getType()).process(txResponse);
								} catch(Exception ex) {
									result = TaskTransactionProcessResult.error("Unknown", ex);
								}

								if(result.isSuccess()) {
									resultRecord.setProcessSuccessFlag(true);
								} else {
									logger.error("{} transaction {} result process error : {} {}", dexTaskId, txRecord.getHash(), result.getError().getCode(), result.getError().getMessage());

									// log exception
									if(result.getError().getCause() != null)
										logger.exception(result.getError().getCause());

									resultRecord.setProcessSuccessFlag(false);
									resultRecord.setErrorcode(result.getError().getCode());
									resultRecord.setErrormessage(result.getError().getMessage());
								}
							}
							else {
								logger.debug("No processor for {} registered", dexTaskId);
							}
						} catch(TaskIntegrityCheckFailedException e) {
							logger.error("Invalid DexTaskId [{}] detected : txHash = {}", memoText, txRecord.getHash());
							resultRecord.setProcessSuccessFlag(false);
							resultRecord.setErrorcode("InvalidTaskID");
							resultRecord.setErrormessage(memoText + " is a invalid taskid. integrity check failed.");
						} catch(Exception ex) {
							logger.exception(ex);
							resultRecord.setProcessSuccessFlag(false);
							resultRecord.setErrorcode(ex.getClass().getSimpleName());
							resultRecord.setErrormessage(ex.getMessage());
						}

						dslContext.attach(resultRecord);
						resultRecord.store();
					}
				}
				// mark last checked tx
				dslContext.update(DEX_STATUS)
						.set(DEX_STATUS.TXMONITORLASTPAGINGTOKEN, txRecord.getPagingToken())
						.execute();

				processed++;
			} catch(Exception ex) {
				logger.exception(ex, "Unidentified exception occured.");
			}
		}

		return processed;
	}
}