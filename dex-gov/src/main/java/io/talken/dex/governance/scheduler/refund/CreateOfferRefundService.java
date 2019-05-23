package io.talken.dex.governance.scheduler.refund;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

//

@Service
@Scope("singleton")
public class CreateOfferRefundService {
//	private static final PrefixedLogger logger = PrefixedLogger.getLogger(CreateOfferRefundService.class);
//
//	@Autowired
//	private GovSettings govSettings;
//
//	@Autowired
//	private DSLContext dslContext;
//
//	@Autowired
//	private StellarNetworkService stellarNetworkService;
//
//	@Autowired
//	private TokenMetaGovService maService;
//
//	@Autowired
//	private SignServerService signerService;
//
//	private static int RETRY_INTERVAL;
//	private static int RETRY_MAX;
//
//	@PostConstruct
//	private void init() {
//		RETRY_INTERVAL = govSettings.getFee().getRefundRetryInterval();
//		RETRY_MAX = govSettings.getFee().getRefundMaxRetry();
//	}
//
//	// check refund tasks
//	@Scheduled(fixedDelay = 10000, initialDelay = 10000)
//	private void do_schedule() {
//
//		/*
//select rt.*, lastLog.success_flag, lastLog.trialNo, lastLog.create_timestamp as logtimestamp
//
//from dex_createoffer_refund_task rt
//       left outer join
//
//     (select *
//      from (select *, ROW_NUMBER() over(PARTITION BY taskId order by create_timestamp desc) AS rn
//            from dex_createoffer_refund_task_txlog) as log
//      where log.rn = 1) as lastLog
//     on rt.taskId = lastLog.taskId
//
//
//where (rt.checked_flag is null or rt.checked_flag = false)
//  and (lastLog.success_flag is null or lastLog.success_flag = false)
//		 */
//
//		Field<Integer> partitionizedRownum = DSL.rowNumber().over().partitionBy(DEX_TASK_REFUNDCREATEOFFERFEE.TASKID).orderBy(DEX_TASK_REFUNDCREATEOFFERFEE.CREATE_TIMESTAMP.desc()).as("rn");
//
//		Table<Record> lastLog = dslContext
//				.select(DSL.asterisk())
//				.from(dslContext
//						.select(DSL.asterisk(), partitionizedRownum)
//						.from(DEX_TASK_REFUNDCREATEOFFERFEE)
//				)
//				.where(partitionizedRownum.eq(1))
//				.asTable("lastLog");
//
//		Field<Boolean> success_flag_field = lastLog.field("success_flag", Boolean.class).as("success_flag");
//
//		List<RefundTask> taskList = dslContext
//				.select(DEX_TQUEUE_REFUNDCREATEOFFERFEE.asterisk(), success_flag_field, lastLog.field("trialNo", Integer.class), lastLog.field("create_timestamp", LocalDateTime.class).as("logTime"))
//				.from(DEX_TQUEUE_REFUNDCREATEOFFERFEE.leftOuterJoin(lastLog).on(DEX_TQUEUE_REFUNDCREATEOFFERFEE.TASKID.eq(lastLog.field("taskId", String.class))))
//				.where(
//						(DEX_TQUEUE_REFUNDCREATEOFFERFEE.FINISHED_FLAG.eq(false).or(DEX_TQUEUE_REFUNDCREATEOFFERFEE.FINISHED_FLAG.isNull()))
//								.and(success_flag_field.eq(false).or(success_flag_field.isNull()))
//				).fetchInto(RefundTask.class);
//
//		for(RefundTask _task : taskList) {
//			try {
//				if(_task.getTrialNo() == null || (_task.getTrialNo() < RETRY_MAX && _task.getLogTime().plusSeconds(RETRY_INTERVAL).isBefore(UTCUtil.getNow()))) {
//					refund(_task);
//				}
//			} catch(Exception ex) {
//				logger.exception(ex, "unexpected refund processing error");
//			}
//		}
//	}
//
//	private void refund(RefundTask taskInfo) {
//		DexTaskRefundcreateofferfeeRecord logRecord = new DexTaskRefundcreateofferfeeRecord();
//		logRecord.setTaskid(taskInfo.getTaskid());
//		if(taskInfo.getTrialNo() == null) logRecord.setTrialno(0);
//		else logRecord.setTrialno(taskInfo.getTrialNo() + 1);
//
//		try {
//			DexTaskId taskId = DexTaskId.decode_taskId(taskInfo.getTaskid());
//
//			logger.info("{} processing started, trial = {}", taskId, logRecord.getTrialno());
//
//			Asset assetType = maService.getAssetType(taskInfo.getRefundassetcode());
//			// pick horizon server
//			Server server = stellarNetworkService.pickServer();
//
//			// prepare accounts
//			KeyPair source = KeyPair.fromAccountId(taskInfo.getFeecollectaccount());
//
//			// load up-to-date information on source account.
//			AccountResponse sourceAccount = server.accounts().account(source);
//
//			KeyPair destination = KeyPair.fromAccountId(taskInfo.getRefundaccount());
//
//			Transaction tx = stellarNetworkService.getTransactionBuilderFor(sourceAccount)
//					.addMemo(Memo.text(taskId.getId()))
//					.addOperation(
//							new PaymentOperation
//									.Builder(destination, assetType, StellarConverter.rawToActualString(taskInfo.getRefundamountraw()))
//									.build()
//					).build();
//
//			// build tx
//			StellarRawTxInfo bareTxInfo = StellarRawTxInfo.build(tx);
//
//			logRecord.setTxSeq(bareTxInfo.getSequence());
//			logRecord.setTxHash(bareTxInfo.getHash());
//			logRecord.setTxXdr(bareTxInfo.getEnvelopeXdr());
//
//			logger.debug("Request sign for {} {}", source.getAccountId(), logRecord.getTxHash());
//			signerService.signStellarTransaction(tx);
//
//			logger.debug("Sending refund TX to stellar network.");
//			SubmitTransactionResponse txResponse = server.submitTransaction(tx);
//
//			if(txResponse.isSuccess()) {
//				logRecord.setSuccessFlag(true);
//				logRecord.setTxResulthash(txResponse.getHash());
//				logRecord.setTxResultxdr(txResponse.getResultXdr());
//				logger.info("Offer fee refund success for {} : trial = {}, {} to {} amount = {} {}", taskId.getId(), logRecord.getTrialno(), taskInfo.getFeecollectaccount(), taskInfo.getRefundaccount(), StellarConverter.rawToActualString(taskInfo.getRefundamountraw()), taskInfo.getRefundassetcode());
//			} else {
//				SubmitTransactionResponse.Extras.ResultCodes resultCodes = txResponse.getExtras().getResultCodes();
//				logRecord.setSuccessFlag(false);
//				logRecord.setErrorposition("submit tx");
//				logRecord.setErrorcode(resultCodes.getTransactionResultCode());
//				StringJoiner sj = new StringJoiner(",");
//				if(resultCodes.getOperationsResultCodes() != null) resultCodes.getOperationsResultCodes().forEach(sj::add);
//				logRecord.setErrormessage(sj.toString());
//				logger.warn("Refund failed for {}, trial = {} : {} {}", logRecord.getTaskid(), logRecord.getTrialno(), logRecord.getErrorcode(), logRecord.getErrormessage());
//			}
//
//		} catch(TaskIntegrityCheckFailedException e) {
//			logger.exception(e, logRecord.getTaskid() + " refund failed");
//			logRecord.setSuccessFlag(false);
//			logRecord.setErrorposition("decoding task ID");
//			logRecord.setErrorcode(e.getClass().getSimpleName());
//			logRecord.setErrormessage(e.getMessage());
//		} catch(TokenMetaNotFoundException e) {
//			logger.exception(e, logRecord.getTaskid() + " refund failed");
//			logRecord.setSuccessFlag(false);
//			logRecord.setErrorposition("get asset type");
//			logRecord.setErrorcode(e.getClass().getSimpleName());
//			logRecord.setErrormessage(e.getMessage());
//		} catch(SigningException e) {
//			logger.exception(e, logRecord.getTaskid() + " refund failed");
//			logRecord.setSuccessFlag(false);
//			logRecord.setErrorposition("sign transaction");
//			logRecord.setErrorcode(e.getClass().getSimpleName());
//			logRecord.setErrormessage(e.getMessage());
//		} catch(IOException e) {
//			logger.exception(e, logRecord.getTaskid() + " refund failed");
//			logRecord.setSuccessFlag(false);
//			logRecord.setErrorposition("stellar api");
//			logRecord.setErrorcode(e.getClass().getSimpleName());
//			logRecord.setErrormessage(e.getMessage());
//		} catch(Exception e) {
//			logger.exception(e, logRecord.getTaskid() + " refund failed");
//			logRecord.setSuccessFlag(false);
//			logRecord.setErrorposition("Unknown");
//			logRecord.setErrorcode(e.getClass().getSimpleName());
//			logRecord.setErrormessage(e.getMessage());
//		}
//
//		dslContext.attach(logRecord);
//		logRecord.store();
//	}
}
