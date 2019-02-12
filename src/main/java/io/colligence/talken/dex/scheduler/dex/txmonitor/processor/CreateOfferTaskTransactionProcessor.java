package io.colligence.talken.dex.scheduler.dex.txmonitor.processor;


import io.colligence.talken.common.persistence.enums.DexTaskTypeEnum;
import io.colligence.talken.common.persistence.jooq.tables.records.DexCreateofferRefundTaskRecord;
import io.colligence.talken.common.persistence.jooq.tables.records.DexCreateofferTaskRecord;
import io.colligence.talken.common.persistence.jooq.tables.records.DexTxResultCreateofferClaimedRecord;
import io.colligence.talken.common.persistence.jooq.tables.records.DexTxResultCreateofferRecord;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.api.dex.DexTaskId;
import io.colligence.talken.dex.api.dex.DexTaskIdService;
import io.colligence.talken.dex.api.mas.ma.ManagedAccountService;
import io.colligence.talken.dex.scheduler.dex.txmonitor.TaskTransactionProcessError;
import io.colligence.talken.dex.scheduler.dex.txmonitor.TaskTransactionProcessResult;
import io.colligence.talken.dex.scheduler.dex.txmonitor.TaskTransactionProcessor;
import io.colligence.talken.dex.util.StellarConverter;
import io.colligence.talken.dex.util.TransactionBlockExecutor;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.responses.TransactionResponse;
import org.stellar.sdk.xdr.*;
import shadow.com.google.common.io.BaseEncoding;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;

import static io.colligence.talken.common.persistence.jooq.Tables.DEX_CREATEOFFER_TASK;

@Component
public class CreateOfferTaskTransactionProcessor implements TaskTransactionProcessor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(CreateOfferTaskTransactionProcessor.class);

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private ManagedAccountService maService;

	@Autowired
	private DexTaskIdService taskIdService;

	@Autowired
	private DataSourceTransactionManager txMgr;

	@Override
	public DexTaskTypeEnum getDexTaskType() {
		return DexTaskTypeEnum.OFFER_CREATE;
	}

	@Override
	public TaskTransactionProcessResult process(DexTaskId dexTaskId, TransactionResponse txResponse) {
		try {
			TransactionBlockExecutor.of(txMgr).transactional(() -> {

				String txHash = txResponse.getHash();
				logger.debug("Processing tx {} for task {}", txHash, dexTaskId);

				Optional<DexCreateofferTaskRecord> opt_createTaskRecord = dslContext.selectFrom(DEX_CREATEOFFER_TASK).where(DEX_CREATEOFFER_TASK.TASKID.eq(dexTaskId.getId())).fetchOptional();

				if(!opt_createTaskRecord.isPresent())
					throw new TaskTransactionProcessError("TaskIdNotFound");

				DexCreateofferTaskRecord createTaskRecord = opt_createTaskRecord.get();

				// TODO : is this always right? same result? even when operation order mismatch?
				// check tx envelope integrity
				try {
					TransactionEnvelope xdr = Transaction.fromEnvelopeXdr(txResponse.getEnvelopeXdr()).toEnvelopeXdr();

					// remove signatures
					xdr.setSignatures(new DecoratedSignature[0]);
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					XdrDataOutputStream xdos = new XdrDataOutputStream(baos);
					TransactionEnvelope.encode(xdos, xdr);
					String bareXdr = Base64.getEncoder().encodeToString(baos.toByteArray());

					if(!bareXdr.equals(createTaskRecord.getTxXdr()))
						throw new TaskTransactionProcessError("TXEnvelopeNotMatch", "{} {} not match", bareXdr, createTaskRecord.getTxXdr());

				} catch(IOException ex) {
					throw new TaskTransactionProcessError("EnvelopeDecodeError", ex);
				}


				TransactionResult result;
				try {
					// decode result
					BaseEncoding base64Encoding = BaseEncoding.base64();
					byte[] bytes = base64Encoding.decode(txResponse.getResultXdr());
					ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
					XdrDataInputStream xdrInputStream = new XdrDataInputStream(inputStream);
					result = TransactionResult.decode(xdrInputStream);
				} catch(IOException ex) {
					throw new TaskTransactionProcessError("ResultDecodeError", ex);
				}


				// extract feeResult and offerResult
				PaymentResult feeResult = null;
				ManageOfferResult offerResult = null;

				for(OperationResult operationResult : result.getResult().getResults()) {
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
						takeRecord.setTaskid(dexTaskId.getId());
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
				resultRecord.setTxid(txResponse.getHash());
				resultRecord.setTaskid(dexTaskId.getId());

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


