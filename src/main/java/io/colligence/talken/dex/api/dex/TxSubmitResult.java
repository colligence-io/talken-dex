package io.colligence.talken.dex.api.dex;

import org.stellar.sdk.responses.SubmitTransactionResponse;
import org.stellar.sdk.xdr.TransactionResult;
import org.stellar.sdk.xdr.XdrDataInputStream;
import shadow.com.google.common.io.BaseEncoding;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class TxSubmitResult {
	private SubmitTransactionResponse response;

	public TxSubmitResult(SubmitTransactionResponse response) {
		this.response = response;
	}

	public boolean isSuccess() {return response.isSuccess();}

	public String getHash() {return response.getHash();}

	public Long getLedger() {return response.getLedger();}

	public String getEnvelopeXdr() {return response.getEnvelopeXdr();}

	public String getResultXdr() {return response.getResultXdr();}

	public SubmitTransactionResponse.Extras getExtras() {return response.getExtras();}

	public TransactionResult decode() throws IOException {
		if(!response.isSuccess()) return null;

		BaseEncoding base64Encoding = BaseEncoding.base64();
		byte[] bytes = base64Encoding.decode(response.getResultXdr());
		ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
		XdrDataInputStream xdrInputStream = new XdrDataInputStream(inputStream);
		return TransactionResult.decode(xdrInputStream);
	}
}
