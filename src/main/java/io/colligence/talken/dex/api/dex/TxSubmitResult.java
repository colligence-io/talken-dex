package io.colligence.talken.dex.api.dex;

import org.stellar.sdk.responses.SubmitTransactionResponse;

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
}
