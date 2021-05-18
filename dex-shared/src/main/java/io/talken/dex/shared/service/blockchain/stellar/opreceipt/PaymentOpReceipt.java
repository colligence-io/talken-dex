package io.talken.dex.shared.service.blockchain.stellar.opreceipt;

import io.talken.dex.shared.service.blockchain.stellar.StellarConverter;
import io.talken.dex.shared.service.blockchain.stellar.StellarOpReceipt;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.stellar.sdk.PaymentOperation;
import org.stellar.sdk.xdr.PaymentResult;
import org.stellar.sdk.xdr.PaymentResultCode;

import java.math.BigDecimal;

/**
 * The type Payment op receipt.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PaymentOpReceipt extends StellarOpReceipt<PaymentOperation, PaymentResult> {

    /**
     * The type Request.
     */
    @Data
	@Builder
	public static class Request {
		private String from;
		private String to;
		private BigDecimal amount;
		private String asset;
	}

    /**
     * The type Result.
     */
    @Data
	@Builder
	public static class Result {
		private PaymentResultCode resultCode;
	}

	private Request request;
	private Result result;

	@Override
	protected void parse(PaymentOperation op, PaymentResult result) {

		this.request = Request.builder()
				.from(op.getSourceAccount() != null ? op.getSourceAccount() : getSourceAccount())
				.to(op.getDestination())
				.amount(StellarConverter.scale(new BigDecimal(op.getAmount())).stripTrailingZeros())
				.asset(assetToString(op.getAsset()))
				.build();

		this.result = Result.builder()
				.resultCode(result.getDiscriminant())
				.build();

		addInvolvedAsset(this.request.getAsset());
		addInvolvedAccount(this.request.getFrom());
		addInvolvedAccount(this.request.getTo());
	}
}
