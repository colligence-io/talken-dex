package io.colligence.talken.dex.scheduler;

import io.colligence.talken.common.util.PrefixedLogger;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Scope("singleton")
public class MakeOfferTaskMonitorService {
	private static final PrefixedLogger logger = PrefixedLogger.getLogger(MakeOfferTaskMonitorService.class);

	@Scheduled(fixedRate = 5000)
	private void checkTask() {

	}

// REFERENCE CODE FOR REFUND
//	public CreateOfferSubmitResult submitCreateOfferTx(long userId, String taskId, String txHash, String txXdr) throws TransactionHashNotMatchException, APIErrorException, TaskIntegrityCheckFailedException, TaskNotFoundException, StellarException, TransactionResultProcessingException {
//		DexTaskId dexTaskId = DexTaskId.fromId(taskId);
//		DexMakeofferTaskRecord taskRecord = dslContext.selectFrom(DEX_MAKEOFFER_TASK)
//				.where(DEX_ANCHOR_TASK.TASKID.eq(taskId))
//				.fetchOptional().orElseThrow(() -> new TaskNotFoundException(taskId));
//
//		if(!taskRecord.getUserId().equals(userId)) throw new TaskIntegrityCheckFailedException(taskId);
//
//		taskRecord.setTxxdrsigned(txXdr);
//
//		APIResult<TxSubmitResult> stellarTxResult = stellarNetworkService.submitTx(taskId, txHash, txXdr);
//
//		taskRecord.setTxSuccessFlag(stellarTxResult.isSuccess());
//		taskRecord.setTxResult(JSONWriter.toJsonStringSafe(stellarTxResult.getData()));
//
//		if(!stellarTxResult.isSuccess()) {
//
//			logger.debug("{} submit failed. : {}", dexTaskId, stellarTxResult.getErrorMessage());
//			taskRecord.setTxErrorcode(stellarTxResult.getErrorCode());
//			taskRecord.setTxErrormessage(stellarTxResult.getErrorMessage());
//			taskRecord.update();
//
//			throw new APIErrorException(stellarTxResult);
//		}
//
//		taskRecord.update();
//
//		CreateOfferSubmitResult result = new CreateOfferSubmitResult();
//		result.setTxSubmitResult(stellarTxResult.getData());
//
//		// decode txResult;
//		TransactionResult txResult;
//		try {
//			txResult = stellarTxResult.getData().decode();
//		} catch(IOException ex) {
//
//			// update task record
//			logger.error("{} txXdr parsing failed. : {}", dexTaskId, ex.getMessage());
//
//			taskRecord.setErrorcode("IOException");
//			taskRecord.setErrormessage("XDR Parsing failed : " + ex.getMessage());
//			taskRecord.update();
//
//			throw new StellarException(ex);
//		}
//
//		// extract feeResult and offerResult
//		PaymentResult feeResult = null;
//		ManageOfferResult offerResult = null;
//
//		for(OperationResult operationResult : txResult.getResult().getResults()) {
//			if(operationResult.getTr().getDiscriminant() == OperationType.PAYMENT) {
//				feeResult = operationResult.getTr().getPaymentResult();
//			}
//			if(operationResult.getTr().getDiscriminant() == OperationType.MANAGE_OFFER) {
//				offerResult = operationResult.getTr().getManageOfferResult();
//			}
//		}
//
//		double makeAmount = 0;
//		double takeAmount = 0;
//		double feeAmount = taskRecord.getFeeamount();
//
//		if(feeAmount != 0 && feeResult == null) {
//			// TODO : WTF?
//
//		}
//
//		if(offerResult == null || offerResult.getSuccess() == null) {
//			// NOTE : WTF?
//
//			logger.error("{} offer result {} processing error : no offer success result", dexTaskId, txHash);
//
//			taskRecord.setErrorcode("NoOfferSuccessResult");
//			taskRecord.update();
//
//			throw new TransactionResultProcessingException(dexTaskId.toString(), "NoOfferSuccessResult");
//		}
//
//		List<DexMakeofferTakelistRecord> takeList = new ArrayList<>();
//		if(offerResult.getSuccess().getOffersClaimed() != null) {
//			for(ClaimOfferAtom claimed : offerResult.getSuccess().getOffersClaimed()) {
//				DexMakeofferTakelistRecord takeRecord = new DexMakeofferTakelistRecord();
//				takeRecord.setTaskid(taskId);
//				takeRecord.setSelleraccount(KeyPair.fromXdrPublicKey(claimed.getSellerID().getAccountID()).getAccountId());
//				takeRecord.setOfferid(claimed.getOfferID().getUint64());
//				takeRecord.setSoldassettype(Asset.fromXdr(claimed.getAssetSold()).getType());
//				double soldAmount = (double) claimed.getAmountSold().getInt64() / 10000000;
//				takeAmount += soldAmount;
//				takeRecord.setSoldamount(soldAmount);
//				takeRecord.setBoughtassettype(Asset.fromXdr(claimed.getAssetBought()).getType());
//				takeRecord.setBoughtamount((double) claimed.getAmountBought().getInt64() / 10000000);
//				takeList.add(takeRecord);
//			}
//		}
//
//		OfferEntry made = offerResult.getSuccess().getOffer().getOffer();
//		if(made != null) {
//			result.setOfferId(made.getOfferID().getUint64());
//			taskRecord.setMadeofferid(made.getOfferID().getUint64());
//			makeAmount = (double) made.getAmount().getInt64() / 10000000;
//			taskRecord.setMadeofferamount(makeAmount);
//		}
//
//		double refundAmount = feeAmount * makeAmount / (makeAmount + takeAmount);
//
//		result.setMakeAmount(makeAmount);
//		result.setTakeAmount(takeAmount);
//		result.setRefundAmount(refundAmount);
//
//		taskRecord.setRefundfeeamount(refundAmount);
//
//		DexTaskId refundTaskId = feeCalculationService.createOfferFeeRefundTask(taskRecord.getFeecollectaccount(), taskRecord.getSourceaccount(), refundAmount);
//
//		taskRecord.setRefundtaskid(refundTaskId.getId());
//
//		try {
//			TransactionBlockExecutor.of(txMgr).transactional(() -> {
//				for(DexMakeofferTakelistRecord takelistRecord : takeList) {
//					dslContext.attach(takelistRecord);
//					takelistRecord.store();
//				}
//				taskRecord.update();
//			});
//		} catch(Exception e) {
//
//			taskRecord.setErrorcode(e.getClass().getName());
//			taskRecord.setErrormessage(e.getMessage());
//			taskRecord.update();
//
//			throw new TransactionResultProcessingException(e, dexTaskId.toString());
//		}
//
//		return result;
//	}
}
