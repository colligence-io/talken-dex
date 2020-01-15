package io.talken.dex.shared.service.blockchain.stellar.opreceipt;

import io.talken.dex.shared.service.blockchain.stellar.StellarOpReceipt;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.stellar.sdk.Operation;
import org.stellar.sdk.xdr.OperationResult;
import org.stellar.sdk.xdr.OperationResultCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DummyOpReceipt extends StellarOpReceipt<Operation, OperationResult> {
	private OperationResultCode resultCode;

	@Override
	protected void parse(Operation op, OperationResult result) {
		resultCode = result.getDiscriminant();
	}
}
