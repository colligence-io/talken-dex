package io.talken.dex.governance.scheduler.txmonitor.processor;


import io.talken.common.persistence.enums.DexTaskTypeEnum;
import io.talken.common.persistence.jooq.tables.records.DexTaskCreateofferRecord;
import io.talken.common.persistence.jooq.tables.records.DexTqueueRefundcreateofferfeeRecord;
import io.talken.common.persistence.jooq.tables.records.DexTxmonCreateofferClaimedRecord;
import io.talken.common.persistence.jooq.tables.records.DexTxmonCreateofferRecord;
import io.talken.common.util.PrefixedLogger;
import io.talken.dex.governance.scheduler.txmonitor.TaskTransactionProcessError;
import io.talken.dex.governance.scheduler.txmonitor.TaskTransactionProcessResult;
import io.talken.dex.governance.scheduler.txmonitor.TaskTransactionProcessor;
import io.talken.dex.governance.scheduler.txmonitor.TaskTransactionResponse;
import io.talken.dex.shared.DexTaskId;
import io.talken.dex.shared.StellarConverter;
import io.talken.dex.shared.TransactionBlockExecutor;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.stellar.sdk.xdr.*;

import java.util.Base64;
import java.util.Optional;

import static io.talken.common.persistence.jooq.Tables.DEX_TASK_CREATEOFFER;

@Component
public class CreateOfferTaskTransactionProcessor implements TaskTransactionProcessor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(CreateOfferTaskTransactionProcessor.class);

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private DataSourceTransactionManager txMgr;

	@Override
	public DexTaskTypeEnum getDexTaskType() {
		return DexTaskTypeEnum.OFFER_CREATE;
	}

	@Override
	public TaskTransactionProcessResult process(Long txmId, TaskTransactionResponse taskTxResponse) {
		try {
			TransactionBlockExecutor.of(txMgr).transactional(() -> {
				Optional<DexTaskCreateofferRecord> opt_createTaskRecord = dslContext.selectFrom(DEX_TASK_CREATEOFFER).where(DEX_TASK_CREATEOFFER.TASKID.eq(taskTxResponse.getTaskId().getId())).fetchOptional();

				if(!opt_createTaskRecord.isPresent())
					throw new TaskTransactionProcessError("TaskIdNotFound");

				DexTaskCreateofferRecord createTaskRecord = opt_createTaskRecord.get();

				// TODO : is this always right? same result? even when operation order mismatch?
				byte[] createBytes = Base64.getDecoder().decode(createTaskRecord.getTxXdr());
				byte[] resultBytes = Base64.getDecoder().decode(taskTxResponse.getResponse().getEnvelopeXdr());

				// -1 means do not compare last byte, which represent signature number
				for(int i = 0; i < createBytes.length - 1; i++) {
					if(createBytes[i] != resultBytes[i])
						throw new TaskTransactionProcessError("TXEnvelopeNotMatch", "{} {} not match", taskTxResponse.getResponse().getEnvelopeXdr(), createTaskRecord.getTxXdr());
				}

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

				long feeAmountRaw = createTaskRecord.getFeeamountraw();

				if(feeAmountRaw != 0 && feeResult == null)
					throw new TaskTransactionProcessError("NoPaymentNode", "No fee payment operation entry found");

				if(offerResult == null || offerResult.getSuccess() == null)
					throw new TaskTransactionProcessError("NoOfferSuccessNode", "No manage offer operation entry found");


				// insert result record
				DexTxmonCreateofferRecord txCreateOfferRecord = new DexTxmonCreateofferRecord();
				txCreateOfferRecord.setTxmId(txmId);
				txCreateOfferRecord.setTaskidCrof(taskTxResponse.getTaskId().getId());

				OfferEntry made = offerResult.getSuccess().getOffer().getOffer();
				if(made != null) {
					txCreateOfferRecord.setOfferid(made.getOfferID().getUint64());

					long makeAmountRaw = made.getAmount().getInt64();
					txCreateOfferRecord.setMakeamountraw(makeAmountRaw);

					long refundAmountRaw = (long) ((double) feeAmountRaw * ((double) makeAmountRaw / (double) createTaskRecord.getSellamountraw()));

					if(refundAmountRaw > 1) {
						// insert refund task
						DexTaskId refundTaskId = DexTaskId.generate_taskId(DexTaskTypeEnum.OFFER_REFUNDFEE);

						DexTqueueRefundcreateofferfeeRecord refundQueueRecord = new DexTqueueRefundcreateofferfeeRecord();
						refundQueueRecord.setTaskid(refundTaskId.getId());
						refundQueueRecord.setTaskidCrof(createTaskRecord.getTaskid());
						refundQueueRecord.setFeecollectaccount(createTaskRecord.getFeecollectaccount());
						refundQueueRecord.setRefundassetcode(createTaskRecord.getFeeassetcode());
						refundQueueRecord.setRefundamountraw(refundAmountRaw);
						refundQueueRecord.setRefundaccount(createTaskRecord.getSourceaccount());
						dslContext.attach(refundQueueRecord);
						refundQueueRecord.store();
						logger.info("{} generated by scheduler. userId = {}", refundTaskId, createTaskRecord.getUserId());
						txCreateOfferRecord.setTaskidRecrof(refundQueueRecord.getTaskid());
					}
				}

				dslContext.attach(txCreateOfferRecord);
				txCreateOfferRecord.store();

				// insert claimed list
				if(offerResult.getSuccess().getOffersClaimed() != null) {
					for(ClaimOfferAtom claimed : offerResult.getSuccess().getOffersClaimed()) {
						DexTxmonCreateofferClaimedRecord txClaimedRecord = new DexTxmonCreateofferClaimedRecord();
						txClaimedRecord.setTxmcoId(txCreateOfferRecord.getId());
						txClaimedRecord.setSelleraccount(createTaskRecord.getSourceaccount());
						txClaimedRecord.setTakeofferid(claimed.getOfferID().getUint64());
						txClaimedRecord.setSoldassettype(StellarConverter.toAssetCode(claimed.getAssetSold()));
						txClaimedRecord.setSoldamountraw(claimed.getAmountSold().getInt64());
						txClaimedRecord.setBoughtassettype(StellarConverter.toAssetCode(claimed.getAssetBought()));
						txClaimedRecord.setBoughtamountraw(claimed.getAmountBought().getInt64());
						dslContext.attach(txClaimedRecord);
						txClaimedRecord.store();
					}
				}


			});
		} catch(TaskTransactionProcessError error) {
			return TaskTransactionProcessResult.error(error);
		} catch(Exception ex) {
			return TaskTransactionProcessResult.error("Processing", ex);
		}
		return TaskTransactionProcessResult.success();
	}
}


