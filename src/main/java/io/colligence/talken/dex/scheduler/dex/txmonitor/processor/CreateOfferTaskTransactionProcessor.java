package io.colligence.talken.dex.scheduler.dex.txmonitor.processor;


import io.colligence.talken.common.persistence.jooq.tables.records.DexCreateofferRefundTaskRecord;
import io.colligence.talken.common.persistence.jooq.tables.records.DexCreateofferResultRecord;
import io.colligence.talken.common.persistence.jooq.tables.records.DexCreateofferResultTakelistRecord;
import io.colligence.talken.common.persistence.jooq.tables.records.DexCreateofferTaskRecord;
import io.colligence.talken.common.util.PrefixedLogger;
import io.colligence.talken.dex.api.dex.DexTaskId;
import io.colligence.talken.dex.api.dex.DexTaskIdService;
import io.colligence.talken.dex.api.mas.ma.ManagedAccountService;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static io.colligence.talken.common.persistence.jooq.Tables.DEX_CREATEOFFER_TASK;

@Component
public class CreateOfferTaskTransactionProcessor implements TaskTransactionProcessor {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(CreateOfferTaskTransactionProcessor.class);

	private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");

	@Autowired
	private DSLContext dslContext;

	@Autowired
	private ManagedAccountService maService;

	@Autowired
	private DexTaskIdService taskIdService;

	@Autowired
	private DataSourceTransactionManager txMgr;

	@Override
	public DexTaskId.Type getDexTaskType() {
		return DexTaskId.Type.OFFER_CREATE;
	}

	@Override
	public TaskTransactionProcessResult process(DexTaskId dexTaskId, TransactionResponse txResponse) {
		String txHash = txResponse.getHash();
		logger.debug("Processing tx {} for task {}", txHash, dexTaskId);

		Optional<DexCreateofferTaskRecord> opt_createTaskRecord = dslContext.selectFrom(DEX_CREATEOFFER_TASK).where(DEX_CREATEOFFER_TASK.TASKID.eq(dexTaskId.getId())).fetchOptional();

		// FIXME : commented out for test
		if(!opt_createTaskRecord.isPresent()) {
			return new TaskTransactionProcessResult("Task id not found");
		}
		DexCreateofferTaskRecord createTaskRecord = opt_createTaskRecord.get();

		DexCreateofferResultRecord resultRecord = new DexCreateofferResultRecord();
		resultRecord.setTaskid(dexTaskId.getId());
		resultRecord.setTxxdr(txResponse.getEnvelopeXdr());
		resultRecord.setTxresultxdr(txResponse.getResultXdr());
		resultRecord.setTxresultmetaxdr(txResponse.getResultMetaXdr());
		resultRecord.setTxcreatedat(LocalDateTime.parse(txResponse.getCreatedAt(), dtf));
		dslContext.attach(resultRecord);
		resultRecord.store();

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

			if(!bareXdr.equals(createTaskRecord.getTxXdr())) {

				resultRecord.setErrorposition("transaction integrity check");
				resultRecord.setErrorcode("TX envelope not match");
				resultRecord.setErrormessage(bareXdr + "\n" + createTaskRecord.getTxXdr());
				resultRecord.setSuccessFlag(false);
				resultRecord.update();

				return new TaskTransactionProcessResult("Transaction integrity check failed.");
			}
		} catch(IOException ex) {

			resultRecord.setErrorposition("parsing envelope xdr");
			resultRecord.setErrorcode(ex.getClass().getSimpleName());
			resultRecord.setErrormessage(ex.getMessage());
			resultRecord.setSuccessFlag(false);
			resultRecord.update();

			return new TaskTransactionProcessResult(ex, "Cannot parse envelope xdr");
		}

		TransactionResult result;
		try {
			BaseEncoding base64Encoding = BaseEncoding.base64();
			byte[] bytes = base64Encoding.decode(txResponse.getResultXdr());
			ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
			XdrDataInputStream xdrInputStream = new XdrDataInputStream(inputStream);
			result = TransactionResult.decode(xdrInputStream);
		} catch(IOException ex) {
			resultRecord.setErrorposition("parsing result xdr");
			resultRecord.setErrorcode(ex.getClass().getSimpleName());
			resultRecord.setErrormessage(ex.getMessage());
			resultRecord.setSuccessFlag(false);
			resultRecord.update();

			return new TaskTransactionProcessResult(ex, "Cannot parse result xdr");
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

		double makeAmount = 0;
		double takeAmount = 0;
		double feeAmount = createTaskRecord.getFeeamount();

		if(feeAmount != 0 && feeResult == null) {
			resultRecord.setErrorposition("extract fee payment operation");
			resultRecord.setErrorcode("NoPaymentNode");
			resultRecord.setErrormessage("NoPaymentNode");
			resultRecord.setSuccessFlag(false);
			resultRecord.update();

			return new TaskTransactionProcessResult("No fee payment operation node found");
		}

		if(offerResult == null || offerResult.getSuccess() == null) {
			resultRecord.setErrorposition("extract offer node");
			resultRecord.setErrorcode("NoOfferSuccessNode");
			resultRecord.setErrormessage("NoOfferSuccessNode");
			resultRecord.setSuccessFlag(false);
			resultRecord.update();

			return new TaskTransactionProcessResult("No offer success node found");
		}

		List<DexCreateofferResultTakelistRecord> takeList = new ArrayList<>();
		if(offerResult.getSuccess().getOffersClaimed() != null) {
			for(ClaimOfferAtom claimed : offerResult.getSuccess().getOffersClaimed()) {
				DexCreateofferResultTakelistRecord takeRecord = new DexCreateofferResultTakelistRecord();
				takeRecord.setTaskid(dexTaskId.getId());
				takeRecord.setSelleraccount(createTaskRecord.getSourceaccount());
				takeRecord.setTakeofferid(claimed.getOfferID().getUint64());
				takeRecord.setSoldassettype(StellarConverter.toAssetCode(claimed.getAssetSold()));
				double soldAmount = StellarConverter.toDouble(claimed.getAmountSold());
				takeAmount += soldAmount;
				takeRecord.setSoldamount(soldAmount);
				takeRecord.setBoughtassettype(StellarConverter.toAssetCode(claimed.getAssetBought()));
				takeRecord.setBoughtamount(StellarConverter.toDouble(claimed.getAmountBought()));
				takeList.add(takeRecord);
			}
		}

		OfferEntry made = offerResult.getSuccess().getOffer().getOffer();
		if(made != null) {
			resultRecord.setOfferid(made.getOfferID().getUint64());
			makeAmount = StellarConverter.toDouble(made.getAmount());
			resultRecord.setMakeamount(makeAmount);
		}

		double refundAmount = feeAmount * makeAmount / (makeAmount + takeAmount);

		try {
			TransactionBlockExecutor.of(txMgr).transactional(() -> {
				for(DexCreateofferResultTakelistRecord takelistRecord : takeList) {
					dslContext.attach(takelistRecord);
					takelistRecord.store();
				}

				DexCreateofferRefundTaskRecord refundTaskRecord = new DexCreateofferRefundTaskRecord();
				refundTaskRecord.setTaskid(taskIdService.generate_taskId(DexTaskId.Type.OFFER_REFUNDFEE).getId());
				refundTaskRecord.setCreateoffertaskid(createTaskRecord.getTaskid());
				refundTaskRecord.setFeecollectaccount(createTaskRecord.getFeecollectaccount());
				refundTaskRecord.setRefundassetcode(createTaskRecord.getFeeassetcode());
				refundTaskRecord.setRefundamount(refundAmount);
				refundTaskRecord.setRefundaccount(createTaskRecord.getSourceaccount());
				dslContext.attach(refundTaskRecord);
				refundTaskRecord.store();

				resultRecord.setRefundtaskid(refundTaskRecord.getTaskid());

				resultRecord.update();
			});
		} catch(Exception e) {

			resultRecord.setErrorposition("update result");
			resultRecord.setErrorcode(e.getClass().getSimpleName());
			resultRecord.setErrormessage(e.getMessage());
			resultRecord.setSuccessFlag(false);

			return new TaskTransactionProcessResult(e);
		}

		return new TaskTransactionProcessResult();
	}
}


