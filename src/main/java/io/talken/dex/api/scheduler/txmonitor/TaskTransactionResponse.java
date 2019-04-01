package io.talken.dex.api.scheduler.txmonitor;

import io.talken.dex.api.shared.DexTaskId;
import org.stellar.sdk.responses.TransactionResponse;
import org.stellar.sdk.xdr.*;
import shadow.com.google.common.io.BaseEncoding;

import java.io.ByteArrayInputStream;

public class TaskTransactionResponse {

	private DexTaskId taskId;
	private TransactionResponse response;
	//	private Transaction tx;
	private TransactionResult result;

	public TaskTransactionResponse(DexTaskId taskId, TransactionResponse response) {
		this.taskId = taskId;
		this.response = response;
		parse();
	}

	private void parse() throws TaskTransactionProcessError {
//		try {
//			// build bare xdr
//			tx = Transaction.fromEnvelopeXdr(response.getEnvelopeXdr());
//		} catch(Exception ex) {
//			throw new TaskTransactionProcessError("EnvelopeDecodeError", ex);
//		}

		try {
			// decode result
			byte[] bytes = BaseEncoding.base64().decode(response.getResultXdr());
			ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
			XdrDataInputStream xdrInputStream = new XdrDataInputStream(inputStream);
			this.result = TransactionResult.decode(xdrInputStream);
		} catch(Exception ex) {
			throw new TaskTransactionProcessError("ResultDecodeError", ex);
		}
	}

	public DexTaskId getTaskId() {
		return taskId;
	}

	public String getTxHash() {return response.getHash();}

	public TransactionResponse getResponse() {
		return response;
	}
//
//	public Transaction getTx() {
//		return tx;
//	}

	public TransactionResult getResult() {
		return result;
	}

	public Long getOfferIdFromResult() {
		if(result != null) {
			if(result.getResult() == null) return null;
			if(result.getResult().getResults() == null || result.getResult().getResults().length == 0) return null;

			// extract feeResult and offerResult
			ManageOfferResult offerResult = null;
			for(OperationResult operationResult : result.getResult().getResults()) {
				if(operationResult.getTr().getDiscriminant() == OperationType.MANAGE_OFFER) {
					offerResult = operationResult.getTr().getManageOfferResult();
				}
			}
			if(offerResult == null) return null;

			if(offerResult.getSuccess() == null) return null;
			if(offerResult.getSuccess().getOffer() == null) return null;
			if(offerResult.getSuccess().getOffer().getOffer() == null) return null;

			return offerResult.getSuccess().getOffer().getOffer().getOfferID().getUint64();

		} else return null;
	}
}
