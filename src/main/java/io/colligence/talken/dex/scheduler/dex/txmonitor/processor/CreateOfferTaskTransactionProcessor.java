package io.colligence.talken.dex.scheduler.dex.txmonitor.processor;


import io.colligence.talken.common.persistence.enums.DexTaskTypeEnum;
import io.colligence.talken.common.persistence.jooq.tables.records.DexCreateofferRefundTaskRecord;
import io.colligence.talken.common.persistence.jooq.tables.records.DexCreateofferTaskRecord;
import io.colligence.talken.common.persistence.jooq.tables.records.DexTxResultCreateofferClaimedRecord;
import io.colligence.talken.common.persistence.jooq.tables.records.DexTxResultCreateofferRecord;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.service.DexTaskIdService;
import io.colligence.talken.dex.scheduler.dex.txmonitor.TaskTransactionProcessError;
import io.colligence.talken.dex.scheduler.dex.txmonitor.TaskTransactionProcessResult;
import io.colligence.talken.dex.scheduler.dex.txmonitor.TaskTransactionProcessor;
import io.colligence.talken.dex.scheduler.dex.txmonitor.TaskTransactionResponse;
import io.colligence.talken.dex.util.StellarConverter;
import io.colligence.talken.dex.util.TransactionBlockExecutor;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.stellar.sdk.xdr.*;

import java.util.Optional;

import static io.colligence.talken.common.persistence.jooq.Tables.DEX_CREATEOFFER_TASK;

@Component
public class CreateOfferTaskTransactionProcessor implements TaskTransactionProcessor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(CreateOfferTaskTransactionProcessor.class);

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private DexTaskIdService taskIdService;

	@Autowired
	private DataSourceTransactionManager txMgr;

	@Override
	public DexTaskTypeEnum getDexTaskType() {
		return DexTaskTypeEnum.OFFER_CREATE;
	}

	@Override
	public TaskTransactionProcessResult process(TaskTransactionResponse taskTxResponse) {
		try {
			TransactionBlockExecutor.of(txMgr).transactional(() -> {

				logger.debug("Processing tx {} for task {}", taskTxResponse.getTxHash(), taskTxResponse.getTaskId());

				Optional<DexCreateofferTaskRecord> opt_createTaskRecord = dslContext.selectFrom(DEX_CREATEOFFER_TASK).where(DEX_CREATEOFFER_TASK.TASKID.eq(taskTxResponse.getTaskId().getId())).fetchOptional();

				if(!opt_createTaskRecord.isPresent())
					throw new TaskTransactionProcessError("TaskIdNotFound");

				DexCreateofferTaskRecord createTaskRecord = opt_createTaskRecord.get();

				// TODO : is this always right? same result? even when operation order mismatch?
				// check tx envelope integrity
				if(!taskTxResponse.getBareXdr().equals(createTaskRecord.getTxXdr()))
					throw new TaskTransactionProcessError("TXEnvelopeNotMatch", "{} {} not match", taskTxResponse.getBareXdr(), createTaskRecord.getTxXdr());

				// extract feeResult and offerResult
				PaymentResult feeResult = null;
				ManageOfferResult offerResult = null;

				for(OperationResult operationResult : taskTxResponse.getResult().getResult().getResults()) {
					if(operationResult.getTr().getDiscriminant() == OperationType.PAYMENT) {
						feeResult = operationResult.getTr().getPaymentResult();
					}
					if(operationResult.getTr().getDiscriminant() == OperationType.MANAGE_OFFER) {
						offerResult = operationResult.getTr().getManageOfferResult();
					}
				}

				double feeAmount = createTaskRecord.getFeeamount();

				if(feeAmount != 0 && feeResult == null)
					throw new TaskTransactionProcessError("NoPaymentNode", "No fee payment operation node found");

				if(offerResult == null || offerResult.getSuccess() == null)
					throw new TaskTransactionProcessError("NoOfferSuccessNode");

				// insert claimed list
				if(offerResult.getSuccess().getOffersClaimed() != null) {
					for(ClaimOfferAtom claimed : offerResult.getSuccess().getOffersClaimed()) {
						DexTxResultCreateofferClaimedRecord takeRecord = new DexTxResultCreateofferClaimedRecord();
						takeRecord.setTaskid(taskTxResponse.getTaskId().getId());
						takeRecord.setSelleraccount(createTaskRecord.getSourceaccount());
						takeRecord.setTakeofferid(claimed.getOfferID().getUint64());
						takeRecord.setSoldassettype(StellarConverter.toAssetCode(claimed.getAssetSold()));
						takeRecord.setSoldamount(StellarConverter.toDouble(claimed.getAmountSold()));
						takeRecord.setBoughtassettype(StellarConverter.toAssetCode(claimed.getAssetBought()));
						takeRecord.setBoughtamount(StellarConverter.toDouble(claimed.getAmountBought()));
						dslContext.attach(takeRecord);
						takeRecord.store();
					}
				}

				// insert result record
				DexTxResultCreateofferRecord resultRecord = new DexTxResultCreateofferRecord();
				resultRecord.setTxid(taskTxResponse.getTxHash());
				resultRecord.setTaskid(taskTxResponse.getTaskId().getId());

				OfferEntry made = offerResult.getSuccess().getOffer().getOffer();
				if(made != null) {
					resultRecord.setOfferid(made.getOfferID().getUint64());

					double makeAmount = StellarConverter.toDouble(made.getAmount());
					resultRecord.setMakeamount(makeAmount);

					double refundAmount = feeAmount * (makeAmount / createTaskRecord.getSellamount());

					// insert refund task
					DexCreateofferRefundTaskRecord refundTaskRecord = new DexCreateofferRefundTaskRecord();
					refundTaskRecord.setTaskid(taskIdService.generate_taskId(DexTaskTypeEnum.OFFER_REFUNDFEE).getId());
					refundTaskRecord.setCreateoffertaskid(createTaskRecord.getTaskid());
					refundTaskRecord.setFeecollectaccount(createTaskRecord.getFeecollectaccount());
					refundTaskRecord.setRefundassetcode(createTaskRecord.getFeeassetcode());
					refundTaskRecord.setRefundamount(refundAmount);
					refundTaskRecord.setRefundaccount(createTaskRecord.getSourceaccount());
					dslContext.attach(refundTaskRecord);
					refundTaskRecord.store();

					resultRecord.setRefundtaskid(refundTaskRecord.getTaskid());
				}

				dslContext.attach(resultRecord);
				resultRecord.store();
			});
		} catch(TaskTransactionProcessError error) {
			return TaskTransactionProcessResult.error(error);
		} catch(Exception ex) {
			return TaskTransactionProcessResult.error("Processing", ex);
		}
		return TaskTransactionProcessResult.success();
	}
}


