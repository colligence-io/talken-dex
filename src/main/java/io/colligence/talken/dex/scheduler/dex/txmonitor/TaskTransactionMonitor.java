package io.colligence.talken.dex.scheduler.dex.txmonitor;

import io.colligence.talken.common.RunningProfile;
import io.colligence.talken.common.persistence.jooq.tables.records.DexStatusRecord;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.api.dex.DexTaskId;
import io.colligence.talken.dex.api.dex.DexTaskIdService;
import io.colligence.talken.dex.exception.TaskIntegrityCheckFailedException;
import io.colligence.talken.dex.service.integration.stellar.StellarNetworkService;
import org.jooq.DSLContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.requests.RequestBuilder;
import org.stellar.sdk.responses.Page;
import org.stellar.sdk.responses.TransactionResponse;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.colligence.talken.common.persistence.jooq.Tables.DEX_STATUS;

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

	private HashMap<DexTaskId.Type, TaskTransactionProcessor> processors = new HashMap<>();

	private ApplicationContext applicationContext;

	@Override
	public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@PostConstruct
	private void init() {
		Map<String, TaskTransactionProcessor> ascBeans = applicationContext.getBeansOfType(TaskTransactionProcessor.class);
		ascBeans.forEach((_name, _asc) -> {
			processors.put(_asc.getDexTaskType(), _asc);
			logger.debug("DexTaskTransactionProcessor for [{}] registered.", _asc.getDexTaskType());
		});
	}

	@Scheduled(fixedDelay = 3000)
	private void checkTask() {
		String lastPagingToken;
		lastPagingToken = getLastPagingToken();
		if(lastPagingToken == null) {
			logger.error("Cannot get lastPagingToken from Stellar network.");
			return;
		}
		processNextTransactions(lastPagingToken);
	}

	private String getLastPagingToken() {
		// reset status if local
		if(RunningProfile.isLocal()) {
			dslContext.deleteFrom(DEX_STATUS).where().execute();
		}

		Optional<DexStatusRecord> opt_status = dslContext.selectFrom(DEX_STATUS).limit(1).fetchOptional();
		if(opt_status.isPresent()) {
			return opt_status.get().getTxmonitorlastpagingtoken();
		}

		try {
			Server server = stellarNetworkService.pickServer();
			Page<TransactionResponse> lastRecord = server.transactions().order(RequestBuilder.Order.DESC).limit(1).execute();
			if(lastRecord.getRecords().size() < 1) {
				return null;
			} else {
				dslContext.insertInto(DEX_STATUS).columns(DEX_STATUS.TXMONITORLASTPAGINGTOKEN).values(lastRecord.getRecords().get(0).getPagingToken()).execute();
				return lastRecord.getRecords().get(0).getPagingToken();
			}
		} catch(Exception ex) {
			logger.exception(ex, "Cannot get last tx from stellar network.");
			return null;
		}
	}

	private void processNextTransactions(String lastPagingToken) {
		Server server = stellarNetworkService.pickServer();
		Page<TransactionResponse> txPage;
		try {
			// 200 is maximum
			txPage = server.transactions().order(RequestBuilder.Order.ASC).cursor(lastPagingToken).limit(200).execute();
		} catch(Exception e) {
			logger.exception(e, "Cannot get last tx page from stellar network.");
			return;
		}

		for(TransactionResponse txRecord : txPage.getRecords()) {
			try {
//				logger.trace("{} {} {} {}", txRecord.getHash(), txRecord.getLedger(), txRecord.getCreatedAt(), txRecord.getPagingToken());
				Memo memo = txRecord.getMemo();
				if(memo instanceof MemoText) {
					String memoText = ((MemoText) memo).getText();
					if(memoText.startsWith("TALKEN")) {
						try {
							DexTaskId dexTaskId = taskIdService.decode_taskId(memoText);
							// run processor
							if(processors.containsKey(dexTaskId.getType())) {

								TaskTransactionProcessResult result;
								try {
									result = processors.get(dexTaskId.getType()).process(dexTaskId, txRecord);
								} catch(Exception ex) {
									result = new TaskTransactionProcessResult(ex);
								}

								if(!result.isSuccess()) {
									if(result.getCause() != null)
										logger.exception(result.getCause());

									logger.error("{} transaction {} result process error : {}", dexTaskId, txRecord.getHash(), result.getMessage());
									// TODO : log error at DB
									// dexTaskId, txRecord, TaskTransactionProcessResult
								}
							}
						} catch(TaskIntegrityCheckFailedException e) {
							logger.warn("Invalid DexTaskId [{}] detected : txHash = {}", memoText, txRecord.getHash());
						}
					}
				}
				// mark last checked tx
				dslContext.update(DEX_STATUS)
						.set(DEX_STATUS.TXMONITORLASTPAGINGTOKEN, txRecord.getPagingToken())
						.execute();
			} catch(Exception ex) {
				logger.exception(ex, "Unidentified exception occured.");
			}
		}

		// TODO : if page count is 200, redo self
	}

//
//	private void getTransactionsStream() {
//		Server server = stellarNetworkService.pickServer();
//
//		SSEStream<TransactionResponse> stream = server.transactions().cursor("").stream((response) -> {
//			String txHash = response.getHash();
//			Memo memo = response.getMemo();
//			if(memo instanceof MemoText) {
//				String memoText = ((MemoText) memo).getText();
//				if(memoText.startsWith("TALKEN")) {
//					try {
//						taskIdService.decode_taskId(memoText);
//						processTransaction(response);
//					} catch(TaskIntegrityCheckFailedException e) {
//						logger.warn("Invalid DexTaskId detected : {}", memoText);
//					}
//				}
//			}
//		});
//	}
}
