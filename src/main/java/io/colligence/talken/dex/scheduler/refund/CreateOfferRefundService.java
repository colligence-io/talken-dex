package io.colligence.talken.dex.scheduler.refund;

import io.colligence.talken.common.persistence.jooq.tables.records.DexCreateofferRefundTaskTxlogRecord;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.common.util.UTCUtil;
import io.colligence.talken.dex.DexSettings;
import io.colligence.talken.dex.api.service.BareTxInfo;
import io.colligence.talken.dex.api.service.DexTaskId;
import io.colligence.talken.dex.api.service.DexTaskIdService;
import io.colligence.talken.dex.api.service.TokenMetaService;
import io.colligence.talken.dex.exception.SigningException;
import io.colligence.talken.dex.exception.TaskIntegrityCheckFailedException;
import io.colligence.talken.dex.exception.TokenMetaDataNotFoundException;
import io.colligence.talken.dex.service.integration.signer.SignServerService;
import io.colligence.talken.dex.service.integration.stellar.StellarNetworkService;
import io.colligence.talken.dex.util.StellarConverter;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.StringJoiner;

import static io.colligence.talken.common.persistence.jooq.Tables.DEX_CREATEOFFER_REFUND_TASK;
import static io.colligence.talken.common.persistence.jooq.Tables.DEX_CREATEOFFER_REFUND_TASK_TXLOG;

@Service
@Scope("singleton")
public class CreateOfferRefundService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(CreateOfferRefundService.class);

	@Autowired
	private DexSettings dexSettings;

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private StellarNetworkService stellarNetworkService;

	@Autowired
	private DexTaskIdService taskIdService;

	@Autowired
	private TokenMetaService maService;

	@Autowired
	private SignServerService signerService;

	private static int RETRY_INTERVAL;
	private static int RETRY_MAX;

	@PostConstruct
	private void init() {
		RETRY_INTERVAL = dexSettings.getFee().getRefundRetryInterval();
		RETRY_MAX = dexSettings.getFee().getRefundMaxRetry();
	}

	// check refund tasks
	@Scheduled(fixedDelay = 10000)
	private void do_schedule() {

		/*
select rt.*, lastLog.success_flag, lastLog.trialNo, lastLog.create_timestamp as logtimestamp

from dex_createoffer_refund_task rt
       left outer join

     (select *
      from (select *, ROW_NUMBER() over(PARTITION BY taskId order by create_timestamp desc) AS rn
            from dex_createoffer_refund_task_txlog) as log
      where log.rn = 1) as lastLog
     on rt.taskId = lastLog.taskId


where (rt.checked_flag is null or rt.checked_flag = false)
  and (lastLog.success_flag is null or lastLog.success_flag = false)
		 */

		Field<Integer> partitionizedRownum = DSL.rowNumber().over().partitionBy(DEX_CREATEOFFER_REFUND_TASK_TXLOG.TASKID).orderBy(DEX_CREATEOFFER_REFUND_TASK_TXLOG.CREATE_TIMESTAMP.desc()).as("rn");

		Table<Record> lastLog = dslContext
				.select(DSL.asterisk())
				.from(dslContext
						.select(DSL.asterisk(), partitionizedRownum)
						.from(DEX_CREATEOFFER_REFUND_TASK_TXLOG)
				)
				.where(partitionizedRownum.eq(1))
				.asTable("lastLog");

		Field<Boolean> success_flag_field = lastLog.field("success_flag", Boolean.class).as("success_flag");

		List<RefundTask> taskList = dslContext
				.select(DEX_CREATEOFFER_REFUND_TASK.asterisk(), success_flag_field, lastLog.field("trialNo", Integer.class), lastLog.field("create_timestamp", LocalDateTime.class).as("logTime"))
				.from(DEX_CREATEOFFER_REFUND_TASK.leftOuterJoin(lastLog).on(DEX_CREATEOFFER_REFUND_TASK.TASKID.eq(lastLog.field("taskId", String.class))))
				.where(
						(DEX_CREATEOFFER_REFUND_TASK.CHECKED_FLAG.eq(false).or(DEX_CREATEOFFER_REFUND_TASK.CHECKED_FLAG.isNull()))
								.and(success_flag_field.eq(false).or(success_flag_field.isNull()))
				).fetchInto(RefundTask.class);

		for(RefundTask _task : taskList) {
			try {
				if(_task.getTrialNo() == null || (_task.getTrialNo() < RETRY_MAX && _task.getLogTime().plusSeconds(RETRY_INTERVAL).isBefore(UTCUtil.getNow()))) {
					refund(_task);
				}
			} catch(Exception ex) {
				logger.exception(ex, "unexpected refund processing error");
			}
		}
	}

	private void refund(RefundTask taskInfo) {
		logger.debug("Starting refund process for {}", taskInfo.getTaskid());

		DexCreateofferRefundTaskTxlogRecord logRecord = new DexCreateofferRefundTaskTxlogRecord();
		logRecord.setTaskid(taskInfo.getTaskid());
		if(taskInfo.getTrialNo() == null) logRecord.setTrialno(0);
		else logRecord.setTrialno(taskInfo.getTrialNo() + 1);
		try {
			DexTaskId taskId = taskIdService.decode_taskId(taskInfo.getTaskid());

			Asset assetType = maService.getAssetType(taskInfo.getRefundassetcode());
			// pick horizon server
			Server server = stellarNetworkService.pickServer();

			// prepare accounts
			KeyPair source = KeyPair.fromAccountId(taskInfo.getFeecollectaccount());

			// load up-to-date information on source account.
			AccountResponse sourceAccount = server.accounts().account(source);

			KeyPair destination = KeyPair.fromAccountId(taskInfo.getRefundaccount());

			Transaction tx = stellarNetworkService.getTransactionBuilderFor(sourceAccount)
					.addMemo(Memo.text(taskId.getId()))
					.addOperation(
							new PaymentOperation
									.Builder(destination, assetType, StellarConverter.rawToDoubleString(taskInfo.getRefundamountraw()))
									.build()
					).build();

			// build tx
			BareTxInfo bareTxInfo = BareTxInfo.build(tx);

			logRecord.setTxSeq(bareTxInfo.getSequence());
			logRecord.setTxHash(bareTxInfo.getHash());
			logRecord.setTxXdr(bareTxInfo.getEnvelopeXdr());

			logger.debug("Request sign for {} {}", source.getAccountId(), logRecord.getTxHash());
			signerService.signTransaction(tx);

			logger.debug("Sending refund TX to stellar network.");
			SubmitTransactionResponse txResponse = server.submitTransaction(tx);

			if(txResponse.isSuccess()) {
				logRecord.setSuccessFlag(true);
				logRecord.setTxResulthash(txResponse.getHash());
				logRecord.setTxResultxdr(txResponse.getResultXdr());
				logger.info("Offer fee refund success for {} : {} to {} amount = {} {}", taskInfo.getTaskid(), taskInfo.getFeecollectaccount(), taskInfo.getRefundaccount(), StellarConverter.rawToDoubleString(taskInfo.getRefundamountraw()), taskInfo.getRefundassetcode());
			} else {
				SubmitTransactionResponse.Extras.ResultCodes resultCodes = txResponse.getExtras().getResultCodes();
				logRecord.setSuccessFlag(false);
				logRecord.setErrorposition("submit tx");
				logRecord.setErrorcode(resultCodes.getTransactionResultCode());
				StringJoiner sj = new StringJoiner(",");
				if(resultCodes.getOperationsResultCodes() != null) resultCodes.getOperationsResultCodes().forEach(sj::add);
				logRecord.setErrormessage(sj.toString());
				logger.warn("Refund failed for {}, trial = {} : {} {}", logRecord.getTaskid(), logRecord.getTrialno(), logRecord.getErrorcode(), logRecord.getErrormessage());
			}

		} catch(TaskIntegrityCheckFailedException e) {
			logger.exception(e, logRecord.getTaskid() + " refund failed");
			logRecord.setSuccessFlag(false);
			logRecord.setErrorposition("decoding task ID");
			logRecord.setErrorcode(e.getClass().getSimpleName());
			logRecord.setErrormessage(e.getMessage());
		} catch(TokenMetaDataNotFoundException e) {
			logger.exception(e, logRecord.getTaskid() + " refund failed");
			logRecord.setSuccessFlag(false);
			logRecord.setErrorposition("get asset type");
			logRecord.setErrorcode(e.getClass().getSimpleName());
			logRecord.setErrormessage(e.getMessage());
		} catch(SigningException e) {
			logger.exception(e, logRecord.getTaskid() + " refund failed");
			logRecord.setSuccessFlag(false);
			logRecord.setErrorposition("sign transaction");
			logRecord.setErrorcode(e.getClass().getSimpleName());
			logRecord.setErrormessage(e.getMessage());
		} catch(IOException e) {
			logger.exception(e, logRecord.getTaskid() + " refund failed");
			logRecord.setSuccessFlag(false);
			logRecord.setErrorposition("stellar api");
			logRecord.setErrorcode(e.getClass().getSimpleName());
			logRecord.setErrormessage(e.getMessage());
		} catch(Exception e) {
			logger.exception(e, logRecord.getTaskid() + " refund failed");
			logRecord.setSuccessFlag(false);
			logRecord.setErrorposition("Unknown");
			logRecord.setErrorcode(e.getClass().getSimpleName());
			logRecord.setErrormessage(e.getMessage());
		}

		dslContext.attach(logRecord);
		logRecord.store();
	}
}
