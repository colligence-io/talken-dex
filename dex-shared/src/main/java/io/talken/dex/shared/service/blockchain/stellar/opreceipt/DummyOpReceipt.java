package io.talken.dex.shared.service.blockchain.stellar.opreceipt;

import io.talken.dex.shared.service.blockchain.stellar.StellarOpReceipt;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.stellar.sdk.Operation;
import org.stellar.sdk.xdr.OperationResult;
import org.stellar.sdk.xdr.OperationResultCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DummyOpReceipt extends StellarOpReceipt<Operation, OperationResult> {

	@Data
	@Builder
	public static class Result {
		private OperationResultCode resultCode;
	}

	private Result result;

	@Override
	protected void parse(Operation op, OperationResult result) {
		this.result = Result.builder()
				.resultCode(result.getDiscriminant())
				.build();
	}
}
