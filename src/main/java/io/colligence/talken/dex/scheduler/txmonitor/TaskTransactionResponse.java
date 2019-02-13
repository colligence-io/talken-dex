package io.colligence.talken.dex.scheduler.txmonitor;

import io.colligence.talken.dex.api.service.DexTaskId;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.responses.TransactionResponse;
import org.stellar.sdk.xdr.*;
import shadow.com.google.common.io.BaseEncoding;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class TaskTransactionResponse {

	private DexTaskId taskId;
	private TransactionResponse response;
	private String bareXdr;
	private TransactionResult result;

	public TaskTransactionResponse(DexTaskId taskId, TransactionResponse response) {
		this.response = response;
	}

	private void parse() throws TaskTransactionProcessError {
		try {
			TransactionEnvelope xdr = Transaction.fromEnvelopeXdr(response.getEnvelopeXdr()).toEnvelopeXdr();

			// remove signatures
			xdr.setSignatures(new DecoratedSignature[0]);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			XdrDataOutputStream xdos = new XdrDataOutputStream(baos);
			TransactionEnvelope.encode(xdos, xdr);
			this.bareXdr = Base64.getEncoder().encodeToString(baos.toByteArray());
		} catch(IOException ex) {
			throw new TaskTransactionProcessError("EnvelopeDecodeError", ex);
		}

		try {
			// decode result
			BaseEncoding base64Encoding = BaseEncoding.base64();
			byte[] bytes = base64Encoding.decode(response.getResultXdr());
			ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
			XdrDataInputStream xdrInputStream = new XdrDataInputStream(inputStream);
			this.result = TransactionResult.decode(xdrInputStream);
		} catch(IOException ex) {
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

	public String getBareXdr() {
		return bareXdr;
	}

	public TransactionResult getResult() {
		return result;
	}

	public Long getOfferIdFromResult() {
		if(response != null) {
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
